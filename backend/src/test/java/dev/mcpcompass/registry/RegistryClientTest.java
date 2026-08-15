package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryClientTest {
    @Test
    void includesCursorAndUpdatedSinceInIncrementalRequest() {
        String path = RegistryClient.serverPath(
                "next cursor", Instant.parse("2026-08-15T10:00:00Z"), 100);

        assertThat(path).isEqualTo("/v0.1/servers?limit=100&cursor=next%20cursor"
                + "&updated_since=2026-08-15T10:00:00Z");
    }
}
