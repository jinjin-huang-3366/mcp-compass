package dev.mcpcompass.github;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;

@Component
class GithubRepositoryClient {
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    GithubRepositoryClient(ObjectMapper objectMapper, GithubEnrichmentProperties properties) {
        this.objectMapper = objectMapper;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(orDefault(properties.connectTimeout(), Duration.ofSeconds(5)))
                .build();
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

    GithubRepositoryMetadata fetch(GithubRepositoryCoordinates coordinates) {
        try {
            JsonNode repository = parse(get("/repos/{owner}/{repository}", coordinates));
            return new GithubRepositoryMetadata(
                    instant(repository, "pushed_at"),
                    fetchLatestRelease(coordinates),
                    repository.path("archived").asBoolean(false),
                    text(repository.path("license"), "spdx_id")
            );
        } catch (GithubClientException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GithubClientException("Unable to fetch GitHub repository metadata", exception);
        }
    }

    private Instant fetchLatestRelease(GithubRepositoryCoordinates coordinates) {
        try {
            JsonNode release = parse(get("/repos/{owner}/{repository}/releases/latest", coordinates));
            return instant(release, "published_at");
        } catch (HttpClientErrorException.NotFound ignored) {
            return null;
        }
    }

    private String get(String path, GithubRepositoryCoordinates coordinates) {
        return restClient.get()
                .uri(path, coordinates.owner(), coordinates.repository())
                .retrieve()
                .body(String.class);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            throw new GithubClientException("GitHub response was empty");
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new GithubClientException("Unable to parse GitHub response", exception);
        }
    }

    private static Instant instant(JsonNode node, String field) {
        String value = text(node, field);
        return value == null ? null : Instant.parse(value);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    static class GithubClientException extends RuntimeException {
        GithubClientException(String message) {
            super(message);
        }

        GithubClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
