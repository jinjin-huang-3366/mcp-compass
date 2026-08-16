package dev.mcpcompass.requirement;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiRequirementClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requestsAndParsesStrictStructuredRequirementOutput() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        String structuredRequirement = """
                {"schemaVersion":"1.0","domain":"source-control","service":"github",
                "requiredCapabilities":["github.issue.read"],
                "forbiddenCapabilities":["github.repository.delete"],
                "constraints":[{"name":"authentication","operator":"EQUALS","value":"oauth2"}]}
                """.replace(System.lineSeparator(), "");
        String response = """
                {
                  "output": [{
                    "type": "message",
                    "content": [{
                      "type": "output_text",
                      "text": %s
                    }]
                  }]
                }
                """.formatted(new ObjectMapper().writeValueAsString(structuredRequirement));
        startServer(response, authorization, requestBody);

        OpenAiRequirementClient client = new OpenAiRequirementClient(
                new ObjectMapper(),
                properties("http://127.0.0.1:" + server.getAddress().getPort())
        );

        StructuredRequirement result = client.analyze(
                "Read GitHub issues using OAuth, but never delete repositories"
        );

        assertThat(authorization.get()).isEqualTo("Bearer test-api-key");
        JsonNode request = new ObjectMapper().readTree(requestBody.get());
        assertThat(request.path("model").stringValue()).isEqualTo("test-model");
        assertThat(request.path("store").booleanValue()).isFalse();
        assertThat(request.path("input").stringValue()).contains("GitHub issues");
        assertThat(request.path("text").path("format").path("type").stringValue()).isEqualTo("json_schema");
        assertThat(request.path("text").path("format").path("strict").booleanValue()).isTrue();
        assertThat(request.path("text").path("format").path("schema")
                .path("additionalProperties").booleanValue()).isFalse();
        assertThat(result.domain()).isEqualTo("source-control");
        assertThat(result.service()).isEqualTo("github");
        assertThat(result.requiredCapabilities()).containsExactly("github.issue.read");
        assertThat(result.forbiddenCapabilities()).containsExactly("github.repository.delete");
        assertThat(result.constraints()).containsExactly(new RequirementConstraint(
                "authentication",
                RequirementConstraint.Operator.EQUALS,
                "oauth2"
        ));
    }

    private void startServer(
            String response,
            AtomicReference<String> authorization,
            AtomicReference<String> requestBody
    ) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
    }

    private static RequirementLlmProperties properties(String baseUrl) {
        return new RequirementLlmProperties(
                true,
                baseUrl,
                "test-api-key",
                "test-model",
                Duration.ofSeconds(2),
                Duration.ofSeconds(2),
                800
        );
    }
}
