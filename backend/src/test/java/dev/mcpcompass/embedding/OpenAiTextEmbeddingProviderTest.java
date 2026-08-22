package dev.mcpcompass.embedding;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiTextEmbeddingProviderTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsABatchedDimensionedRequestAndRestoresResponseOrder() throws IOException {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            requestBody.set(OBJECT_MAPPER.readTree(exchange.getRequestBody()));
            byte[] response = responseWithReversedIndexes().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        EmbeddingProperties properties = new EmbeddingProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "test-key",
                "text-embedding-3-small",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                100,
                0.35
        );
        OpenAiTextEmbeddingProvider provider = new OpenAiTextEmbeddingProvider(OBJECT_MAPPER, properties);

        List<EmbeddingVector> embeddings = provider.embed(List.of("first", "second"));

        assertThat(requestBody.get().path("model").stringValue()).isEqualTo("text-embedding-3-small");
        assertThat(requestBody.get().path("dimensions").intValue()).isEqualTo(384);
        assertThat(requestBody.get().path("encoding_format").stringValue()).isEqualTo("float");
        assertThat(requestBody.get().path("input")).extracting(JsonNode::stringValue)
                .containsExactly("first", "second");
        assertThat(embeddings).extracting(vector -> vector.values().getFirst())
                .containsExactly(0.1, 0.2);
    }

    private static String responseWithReversedIndexes() {
        return """
                {"object":"list","model":"text-embedding-3-small","data":[
                  {"object":"embedding","index":1,"embedding":%s},
                  {"object":"embedding","index":0,"embedding":%s}
                ]}
                """.formatted(vectorJson(0.2), vectorJson(0.1));
    }

    private static String vectorJson(double firstValue) {
        return IntStream.range(0, EmbeddingProperties.DIMENSIONS)
                .mapToObj(index -> String.valueOf(index == 0 ? firstValue : 0.0))
                .collect(Collectors.joining(",", "[", "]"));
    }
}
