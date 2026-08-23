package dev.mcpcompass.github;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GithubRepositoryClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsActivityReleaseArchiveAndLicenseMetadata() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/example/server", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"pushed_at":"2026-08-20T10:00:00Z","archived":true,
                     "license":{"spdx_id":"Apache-2.0"}}
                    """);
        });
        server.createContext("/repos/example/server/releases/latest", exchange -> respond(exchange, 200,
                "{\"published_at\":\"2026-07-01T12:00:00Z\"}"));
        server.start();

        GithubRepositoryClient client = new GithubRepositoryClient(new ObjectMapper(), properties("test-token"));

        GithubRepositoryMetadata metadata = client.fetch(new GithubRepositoryCoordinates("example", "server"));

        assertThat(metadata).isEqualTo(new GithubRepositoryMetadata(
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z"),
                true,
                "Apache-2.0"
        ));
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
    }

    @Test
    void representsRepositoryWithoutAReleaseAsNullReleaseTime() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/example/server", exchange -> respond(exchange, 200,
                "{\"pushed_at\":\"2026-08-20T10:00:00Z\",\"archived\":false,\"license\":null}"));
        server.createContext("/repos/example/server/releases/latest", exchange -> respond(exchange, 404, "{}"));
        server.start();

        GithubRepositoryMetadata metadata = new GithubRepositoryClient(new ObjectMapper(), properties(""))
                .fetch(new GithubRepositoryCoordinates("example", "server"));

        assertThat(metadata.latestReleaseAt()).isNull();
        assertThat(metadata.licenseSpdx()).isNull();
    }

    private GithubEnrichmentProperties properties(String token) {
        return new GithubEnrichmentProperties(true,
                "http://127.0.0.1:" + server.getAddress().getPort(), token,
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
