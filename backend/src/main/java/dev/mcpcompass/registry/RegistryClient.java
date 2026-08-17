package dev.mcpcompass.registry;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class RegistryClient {
    private final RestClient restClient;
    private final RegistryPayloadMapper payloadMapper;
    private final RegistryProperties properties;

    public RegistryClient(RegistryPayloadMapper payloadMapper, RegistryProperties properties) {
        this.payloadMapper = payloadMapper;
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(defaultIfNull(properties.connectTimeout(), Duration.ofSeconds(5)))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(defaultIfNull(properties.readTimeout(), Duration.ofSeconds(15)));
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public RegistryPage fetchServers(String cursor, Instant updatedSince) {
        String path = serverPath(cursor, updatedSince, properties.pageSize());

        String body = restClient.get()
                .uri(path)
                .retrieve()
                .body(String.class);
        return payloadMapper.map(body);
    }

    static String serverPath(String cursor, Instant updatedSince, int pageSize) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/v0.1/servers")
                .queryParam("limit", pageSize);
        if (cursor != null && !cursor.isBlank()) {
            uri.queryParam("cursor", cursor);
        }
        if (updatedSince != null) {
            uri.queryParam("updated_since", updatedSince.toString());
        }

        return uri.build().encode().toUriString();
    }

    private static Duration defaultIfNull(Duration value, Duration fallback) {
        return value == null ? fallback : value;
    }

    public record RegistryPage(List<RegistryServerPayload> servers, String nextCursor) {
    }

    public record RegistryServerPayload(
            String name,
            String title,
            String description,
            String version,
            String status,
            String rawMetadata,
            boolean officialRegistryProvenance,
            String repositoryUrl,
            int packageCount,
            int remoteCount,
            List<RegistryToolPayload> tools,
            List<RegistryCapabilityPayload> capabilities
    ) {
        public RegistryServerPayload {
            packageCount = Math.max(0, packageCount);
            remoteCount = Math.max(0, remoteCount);
            tools = tools == null ? List.of() : List.copyOf(tools);
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }

        public RegistryServerPayload(
                String name,
                String title,
                String description,
                String version,
                String status,
                String rawMetadata,
                List<RegistryToolPayload> tools,
                List<RegistryCapabilityPayload> capabilities
        ) {
            this(name, title, description, version, status, rawMetadata, false, null, 0, 0, tools, capabilities);
        }

        public RegistryServerPayload(
                String name,
                String title,
                String description,
                String version,
                String status,
                String rawMetadata
        ) {
            this(name, title, description, version, status, rawMetadata, false, null, 0, 0, List.of(), List.of());
        }
    }

    public record RegistryToolPayload(
            String name,
            String description,
            String inputSchema,
            List<RegistryCapabilityPayload> capabilities
    ) {
        public RegistryToolPayload {
            capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        }
    }

    public record RegistryCapabilityPayload(String name, String description) {
    }

    public static class RegistryClientException extends RuntimeException {
        public RegistryClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
