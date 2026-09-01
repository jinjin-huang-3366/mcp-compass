package dev.mcpcompass.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GithubRepositoryContentClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsRecordedReadmeAndStaticToolsWithProvenanceHashesAndBounds() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/example/server/readme",
                exchange -> fixture(exchange, "/fixtures/github/readme-content.json"));
        server.createContext("/repos/example/server/contents/.mcp/server.json",
                exchange -> fixture(exchange, "/fixtures/github/static-tool-content.json"));
        server.start();

        List<GithubRepositoryArtifact> artifacts = client(1_024).fetch(
                new GithubRepositoryCoordinates("example", "server"));

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts.getFirst()).satisfies(readme -> {
            assertThat(readme.kind()).isEqualTo(GithubRepositoryArtifact.Kind.README);
            assertThat(readme.sourcePath()).isEqualTo("README.md");
            assertThat(readme.sourceRevision()).isEqualTo("1111111111111111111111111111111111111111");
            assertThat(readme.content()).contains("Tools for GitHub issues");
            assertThat(readme.contentSha256()).matches("[0-9a-f]{64}");
            assertThat(readme.tools()).isEmpty();
        });
        assertThat(artifacts.get(1)).satisfies(metadata -> {
            assertThat(metadata.kind()).isEqualTo(GithubRepositoryArtifact.Kind.STATIC_TOOL_METADATA);
            assertThat(metadata.sourcePath()).isEqualTo(".mcp/server.json");
            assertThat(metadata.sourceUrl()).endsWith("/repos/example/server/contents/.mcp/server.json");
            assertThat(metadata.tools()).singleElement().satisfies(tool -> {
                assertThat(tool.name()).isEqualTo("list_issues");
                assertThat(tool.description()).isEqualTo("List repository issues");
                assertThat(tool.inputSchema()).contains("\"owner\"");
            });
        });
    }

    @Test
    void rejectsDecodedContentThatExceedsTheConfiguredBound() throws IOException {
        String content = java.util.Base64.getEncoder().encodeToString("x".repeat(1_025)
                .getBytes(StandardCharsets.UTF_8));
        String body = "{\"path\":\"README.md\",\"sha\":\"abc\",\"size\":1025,"
                + "\"encoding\":\"base64\",\"content\":\"" + content + "\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/example/server/readme", exchange -> respond(exchange, 200, body));
        server.start();

        assertThatThrownBy(() -> client(1_024).fetch(new GithubRepositoryCoordinates("example", "server")))
                .isInstanceOf(GithubRepositoryContentClient.GithubContentException.class)
                .hasMessageContaining("exceeds the configured bound");
    }

    private GithubRepositoryContentClient client(int maxBytes) {
        GithubEnrichmentProperties properties = new GithubEnrichmentProperties(
                true, "http://127.0.0.1:" + server.getAddress().getPort(), "",
                Duration.ofSeconds(1), Duration.ofSeconds(2), maxBytes, 10, 2_048,
                List.of(".mcp/server.json"));
        return new GithubRepositoryContentClient(new ObjectMapper(), properties);
    }

    private void fixture(HttpExchange exchange, String resource) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(resource))) {
            respond(exchange, 200, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
