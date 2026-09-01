package dev.mcpcompass.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Reads bounded GitHub repository data only. It never downloads packages or executes repository content. */
@Component
class GithubRepositoryContentClient {
    private static final int RESPONSE_OVERHEAD_BYTES = 16_384;

    private final ObjectMapper objectMapper;
    private final GithubEnrichmentProperties properties;
    private final RestClient restClient;

    GithubRepositoryContentClient(ObjectMapper objectMapper, GithubEnrichmentProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(orDefault(properties.connectTimeout(), Duration.ofSeconds(5))).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(orDefault(properties.readTimeout(), Duration.ofSeconds(15)));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "mcp-compass")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .requestFactory(requestFactory);
        if (properties.token() != null && !properties.token().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token());
        }
        this.restClient = builder.build();
    }

    List<GithubRepositoryArtifact> fetch(GithubRepositoryCoordinates coordinates) {
        List<GithubRepositoryArtifact> artifacts = new ArrayList<>();
        fetchContent(coordinates, null, GithubRepositoryArtifact.Kind.README, "text/markdown")
                .ifPresent(artifacts::add);
        for (String path : properties.boundedStaticMetadataPaths()) {
            fetchContent(coordinates, path, GithubRepositoryArtifact.Kind.STATIC_TOOL_METADATA, "application/json")
                    .ifPresent(artifacts::add);
        }
        return List.copyOf(artifacts);
    }

    private Optional<GithubRepositoryArtifact> fetchContent(
            GithubRepositoryCoordinates coordinates,
            String sourcePath,
            GithubRepositoryArtifact.Kind kind,
            String mediaType
    ) {
        String endpoint = sourcePath == null
                ? repositoryEndpoint(coordinates) + "/readme"
                : repositoryEndpoint(coordinates) + "/contents/"
                        + UriUtils.encodePath(sourcePath, StandardCharsets.UTF_8);
        String body = getBounded(endpoint);
        if (body == null) {
            return Optional.empty();
        }
        JsonNode envelope = parse(body);
        String encodedContent = text(envelope, "content");
        long declaredSize = envelope.path("size").asLong(-1);
        if (!"base64".equals(text(envelope, "encoding")) || encodedContent == null || declaredSize < 0
                || declaredSize > properties.effectiveMaxArtifactBytes()) {
            throw new GithubContentException("GitHub content metadata is unsupported or exceeds the configured bound");
        }

        byte[] bytes;
        try {
            bytes = Base64.getMimeDecoder().decode(encodedContent);
        } catch (IllegalArgumentException exception) {
            throw new GithubContentException("GitHub content was not valid base64", exception);
        }
        if (bytes.length != declaredSize || bytes.length > properties.effectiveMaxArtifactBytes()) {
            throw new GithubContentException("GitHub content size did not match its bounded metadata");
        }
        String content = utf8(bytes);
        List<GithubRepositoryArtifact.StaticTool> tools = kind == GithubRepositoryArtifact.Kind.STATIC_TOOL_METADATA
                ? parseTools(content) : List.of();
        return Optional.of(new GithubRepositoryArtifact(
                kind, sourcePath == null ? text(envelope, "path") : sourcePath,
                properties.baseUrl() + endpoint, text(envelope, "sha"), mediaType,
                content, sha256(bytes), tools));
    }

    private String getBounded(String endpoint) {
        int maxResponseBytes = Math.addExact(
                Math.multiplyExact(properties.effectiveMaxArtifactBytes(), 2), RESPONSE_OVERHEAD_BYTES);
        return restClient.get().uri(endpoint).exchange((request, response) -> {
            if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new GithubContentException("GitHub content request failed with status "
                        + response.getStatusCode().value());
            }
            if (response.getHeaders().getContentLength() > maxResponseBytes) {
                throw new GithubContentException("GitHub content response exceeds the configured bound");
            }
            try {
                byte[] responseBytes = response.getBody().readNBytes(maxResponseBytes + 1);
                if (responseBytes.length > maxResponseBytes) {
                    throw new GithubContentException("GitHub content response exceeds the configured bound");
                }
                return utf8(responseBytes);
            } catch (IOException exception) {
                throw new GithubContentException("Unable to read GitHub content response", exception);
            }
        });
    }

    private List<GithubRepositoryArtifact.StaticTool> parseTools(String content) {
        JsonNode root = parse(content);
        JsonNode tools = root.path("tools");
        if (!tools.isArray()) {
            tools = root.path("server").path("tools");
        }
        if (!tools.isArray()) {
            return List.of();
        }
        List<GithubRepositoryArtifact.StaticTool> result = new ArrayList<>();
        for (JsonNode tool : tools) {
            if (result.size() >= properties.effectiveMaxStaticTools()) {
                break;
            }
            String name = boundedText(tool, "name", 255);
            if (name == null) {
                continue;
            }
            String description = boundedText(tool, "description", 4_096);
            JsonNode schema = tool.has("inputSchema") ? tool.path("inputSchema") : tool.path("input_schema");
            String inputSchema = null;
            if (schema.isObject()) {
                try {
                    String candidate = objectMapper.writeValueAsString(schema);
                    if (candidate.getBytes(StandardCharsets.UTF_8).length
                            <= properties.effectiveMaxToolSchemaBytes()) {
                        inputSchema = candidate;
                    }
                } catch (Exception exception) {
                    throw new GithubContentException("Unable to serialize static tool schema", exception);
                }
            }
            result.add(new GithubRepositoryArtifact.StaticTool(name, description, inputSchema));
        }
        return List.copyOf(result);
    }

    private JsonNode parse(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new GithubContentException("Unable to parse bounded GitHub content", exception);
        }
    }

    private static String boundedText(JsonNode node, String field, int maxCharacters) {
        String value = text(node, field);
        return value != null && value.length() <= maxCharacters ? value : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static String repositoryEndpoint(GithubRepositoryCoordinates coordinates) {
        return "/repos/" + UriUtils.encodePathSegment(coordinates.owner(), StandardCharsets.UTF_8)
                + "/" + UriUtils.encodePathSegment(coordinates.repository(), StandardCharsets.UTF_8);
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new GithubContentException("GitHub content was not valid UTF-8", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    static class GithubContentException extends RuntimeException {
        GithubContentException(String message) { super(message); }
        GithubContentException(String message, Throwable cause) { super(message, cause); }
    }
}
