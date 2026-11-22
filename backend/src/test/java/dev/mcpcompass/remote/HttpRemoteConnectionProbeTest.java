package dev.mcpcompass.remote;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRemoteConnectionProbeTest {
    private final HttpRemoteConnectionProbe probe = new HttpRemoteConnectionProbe(new ObjectMapper());

    @Test
    void classifiesAuthReachabilityAndProtocolUsingInitializeOnly() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/auth", exchange -> { method.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.createContext("/forbidden", exchange -> { exchange.sendResponseHeaders(403, -1); exchange.close(); });
        server.createContext("/ready", exchange -> reply(exchange, 200, "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"protocolVersion\":\"2025-06-18\"}}"));
        server.createContext("/wrong", exchange -> reply(exchange, 200, "<html>not MCP</html>"));
        server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(404, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(probe.check(URI.create(base + "/auth")).state()).isEqualTo(RemoteConnectionState.AUTH_REQUIRED);
            assertThat(probe.check(URI.create(base + "/forbidden")).state()).isEqualTo(RemoteConnectionState.AUTH_REQUIRED);
            assertThat(probe.check(URI.create(base + "/ready")).state()).isEqualTo(RemoteConnectionState.REACHABLE);
            assertThat(probe.check(URI.create(base + "/wrong")).state()).isEqualTo(RemoteConnectionState.PROTOCOL_MISMATCH);
            assertThat(probe.check(URI.create(base + "/missing")).state()).isEqualTo(RemoteConnectionState.PROTOCOL_MISMATCH);
            assertThat(method.get()).contains("\"method\":\"initialize\"").doesNotContain("tools/call");
        } finally {
            server.stop(0);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
