package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryRemoteConnectionSourceTest {
    private final RegistryRemoteConnectionSource source = new RegistryRemoteConnectionSource(new ObjectMapper());

    @Test
    void selectsDeclaredStreamableHttpWithoutExecutingOrGuessingSseTransport() {
        var server = server("""
                {"server":{"remotes":[
                    {"type":"sse","url":"https://example.org/sse"},
                    {"type":"streamable-http","url":"https://example.org/mcp"}
                ]}}
                """);
        assertThat(source.endpoint(server)).hasValue(java.net.URI.create("https://example.org/mcp"));
        assertThat(source.endpoint(server("{\"server\":{\"remotes\":[{\"type\":\"sse\",\"url\":\"https://example.org/sse\"}]}}")))
                .isEmpty();
    }

    private static McpServerEntity server(String raw) {
        var server = McpServerEntity.create("example/remote", Instant.EPOCH);
        server.updateFrom(new RegistryClient.RegistryServerPayload("example/remote", "Remote", "Remote MCP", "1",
                "active", raw, true, null, 0, 1, List.of(), List.of()), Instant.EPOCH);
        return server;
    }
}
