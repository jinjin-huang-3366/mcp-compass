package dev.mcpcompass.remote;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

@Component
public class HttpRemoteConnectionProbe implements RemoteConnectionProbe {
    private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
            + "\"params\":{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},"
            + "\"clientInfo\":{\"name\":\"mcp-compass-connection-check\",\"version\":\"1\"}}}";
    private static final int MAX_BODY = 16_384;
    private final ObjectMapper mapper;

    public HttpRemoteConnectionProbe(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Result check(URI endpoint) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        try {
            HttpURLConnection connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(1000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json, text/event-stream");
            connection.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            connection.setDoOutput(true);
            try {
                try (var out = connection.getOutputStream()) {
                    out.write(INITIALIZE.getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                if (status == 401 || status == 403) {
                    return new Result(RemoteConnectionState.AUTH_REQUIRED, "HTTP " + status + " requires authorization");
                }
                if (status >= 300 && status < 400) {
                    return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "HTTP redirect " + status + " was not followed");
                }
                if (status == 429) {
                    return new Result(RemoteConnectionState.NOT_CHECKED, "HTTP 429 rate limited");
                }
                if (status == 400 || status == 404 || status == 405 || status == 406 || status == 415 || status == 422) {
                    return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "HTTP " + status + " did not accept MCP initialize");
                }
                if (status < 200 || status >= 300) {
                    return new Result(RemoteConnectionState.UNREACHABLE, "HTTP " + status);
                }
                String content;
                try (var body = connection.getInputStream()) {
                    if (connection.getContentType() != null
                            && connection.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream")) {
                        var reader = new java.io.InputStreamReader(body, StandardCharsets.UTF_8);
                        StringBuilder line = new StringBuilder();
                        int total = 0;
                        content = null;
                        int next;
                        while (total++ < MAX_BODY && System.nanoTime() < deadline && (next = reader.read()) != -1) {
                            if (next == '\n') {
                                if (line.toString().startsWith("data:")) {
                                    content = line.substring(5).trim();
                                    break;
                                }
                                line.setLength(0);
                            } else if (next != '\r') {
                                line.append((char) next);
                            }
                        }
                        if (content == null) {
                            return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "No bounded MCP event data");
                        }
                    } else {
                        var output = new java.io.ByteArrayOutputStream();
                        byte[] chunk = new byte[1024];
                        int count;
                        while (output.size() <= MAX_BODY && System.nanoTime() < deadline
                                && (count = body.read(chunk)) != -1) {
                            output.write(chunk, 0, count);
                        }
                        if (output.size() > MAX_BODY) {
                            return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "Initialize response exceeded size limit");
                        }
                        if (System.nanoTime() >= deadline) {
                            return new Result(RemoteConnectionState.UNREACHABLE, "Initialize response timed out");
                        }
                        content = output.toString(StandardCharsets.UTF_8);
                    }
                }
                JsonNode json;
                try {
                    json = mapper.readTree(content);
                } catch (RuntimeException invalid) {
                    return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "Initialize response was not JSON");
                }
                if (json.path("jsonrpc").asText().equals("2.0")
                        && json.path("result").path("protocolVersion").isString()) {
                    return new Result(RemoteConnectionState.REACHABLE, "MCP initialize succeeded");
                }
                return new Result(RemoteConnectionState.PROTOCOL_MISMATCH, "No valid MCP initialize result");
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            return new Result(RemoteConnectionState.UNREACHABLE, "Connection or response timed out or failed");
        }
    }
}
