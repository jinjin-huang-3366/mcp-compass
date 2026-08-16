package dev.mcpcompass.registry;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryClientIntegrationTest {
    // Recorded 2026-08-16 from GET /v0.1/servers with search, latest-version,
    // and include-deleted filters against the official MCP Registry.
    private static final String FIXTURE =
            "/fixtures/registry/official-registry-v0.1-deleted-server-page.json";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesAndMapsRecordedOfficialRegistryPage() throws IOException {
        byte[] response;
        try (InputStream fixture = Objects.requireNonNull(getClass().getResourceAsStream(FIXTURE))) {
            response = fixture.readAllBytes();
        }
        AtomicReference<URI> requestUri = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v0.1/servers", exchange -> {
            requestUri.set(exchange.getRequestURI());
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        RegistryProperties properties = new RegistryProperties(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                10,
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                new RegistryProperties.Sync(false, 3_600_000, 1));
        RegistryClient client = new RegistryClient(
                new RegistryPayloadMapper(new ObjectMapper()), properties);

        RegistryClient.RegistryPage page = client.fetchServers(null, null);

        assertThat(requestUri.get().getPath()).isEqualTo("/v0.1/servers");
        assertThat(requestUri.get().getRawQuery()).isEqualTo("limit=10");
        assertThat(page.nextCursor()).isNull();
        assertThat(page.servers()).singleElement().satisfies(registryServer -> {
            assertThat(registryServer.name()).isEqualTo("ac.inference.sh/inference");
            assertThat(registryServer.title()).isEqualTo("inference.sh");
            assertThat(registryServer.version()).isEqualTo("1.0.0");
            assertThat(registryServer.status()).isEqualTo("deleted");
            assertThat(registryServer.rawMetadata())
                    .contains("io.modelcontextprotocol.registry/official")
                    .contains("\"status\":\"deleted\"");
        });
    }
}
