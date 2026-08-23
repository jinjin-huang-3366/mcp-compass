package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeHttpOpenApiUrlFetcherTest {
    @Test
    void rejectsNonHttpsUrlBeforeFetching() {
        assertThatThrownBy(() -> SafeHttpOpenApiUrlFetcher.validateRemoteUri(
                URI.create("http://example.com/openapi.json")))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void rejectsLoopbackAddress() {
        assertThatThrownBy(() -> SafeHttpOpenApiUrlFetcher.validateRemoteUri(
                URI.create("https://127.0.0.1/openapi.json")))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("public network");
    }

    @Test
    void rejectsCredentialsAndNonDefaultPort() {
        assertThatThrownBy(() -> SafeHttpOpenApiUrlFetcher.validateRemoteUri(
                URI.create("https://user@example.com:8443/openapi.json")))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("credentials");
    }
}
