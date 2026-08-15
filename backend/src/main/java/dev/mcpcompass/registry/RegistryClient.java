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
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/v0.1/servers")
                .queryParam("limit", properties.pageSize());
        if (cursor != null && !cursor.isBlank()) {
            uri.queryParam("cursor", cursor);
        }
        if (updatedSince != null) {
            uri.queryParam("updated_since", updatedSince.toString());
        }

        String body = restClient.get()
                .uri(uri.build().encode().toUriString())
                .retrieve()
                .body(String.class);
        return payloadMapper.map(body);
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
            String rawMetadata
    ) {
    }

    public static class RegistryClientException extends RuntimeException {
        public RegistryClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
