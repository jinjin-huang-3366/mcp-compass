package dev.mcpcompass.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.search.vector", name = "enabled", havingValue = "true")
class OpenAiTextEmbeddingProvider implements TextEmbeddingProvider {
    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;
    private final RestClient restClient;

    OpenAiTextEmbeddingProvider(ObjectMapper objectMapper, EmbeddingProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = validate(properties);

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<EmbeddingVector> embed(List<String> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        if (inputs.isEmpty()) {
            return List.of();
        }
        if (inputs.stream().anyMatch(input -> input == null || input.isBlank())) {
            throw new IllegalArgumentException("embedding inputs must not be blank");
        }

        try {
            String response = restClient.post()
                    .uri("/v1/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "input", inputs,
                            "dimensions", EmbeddingProperties.DIMENSIONS,
                            "encoding_format", "float"
                    ))
                    .retrieve()
                    .body(String.class);
            return parseResponse(response, inputs.size());
        } catch (TextEmbeddingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TextEmbeddingException("Unable to create configured text embeddings", exception);
        }
    }

    private List<EmbeddingVector> parseResponse(String response, int expectedCount) {
        if (response == null || response.isBlank()) {
            throw new TextEmbeddingException("Embedding response was empty");
        }
        try {
            JsonNode data = objectMapper.readTree(response).path("data");
            if (!data.isArray() || data.size() != expectedCount) {
                throw new TextEmbeddingException("Embedding response count did not match the request");
            }

            List<IndexedEmbedding> indexed = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode rawIndex = item.path("index");
                if (!rawIndex.isIntegralNumber()) {
                    throw new TextEmbeddingException("Embedding response item did not contain an index");
                }
                int index = rawIndex.intValue();
                JsonNode rawValues = item.path("embedding");
                if (!rawValues.isArray()) {
                    throw new TextEmbeddingException("Embedding response item did not contain a vector");
                }
                List<Double> values = new ArrayList<>();
                rawValues.forEach(value -> {
                    if (!value.isNumber()) {
                        throw new TextEmbeddingException("Embedding response vector contained a non-number");
                    }
                    values.add(value.doubleValue());
                });
                indexed.add(new IndexedEmbedding(
                        index,
                        new EmbeddingVector(properties.model(), values)
                ));
            }
            indexed.sort(Comparator.comparingInt(IndexedEmbedding::index));
            for (int index = 0; index < indexed.size(); index++) {
                if (indexed.get(index).index() != index) {
                    throw new TextEmbeddingException("Embedding response indexes were incomplete");
                }
            }
            return indexed.stream().map(IndexedEmbedding::embedding).toList();
        } catch (TextEmbeddingException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TextEmbeddingException("Unable to parse embedding response", exception);
        }
    }

    private static EmbeddingProperties validate(EmbeddingProperties properties) {
        Objects.requireNonNull(properties, "properties must not be null");
        requireNonBlank(properties.baseUrl(), "baseUrl");
        requireNonBlank(properties.apiKey(), "apiKey");
        requireNonBlank(properties.model(), "model");
        requirePositive(properties.connectTimeout(), "connectTimeout");
        requirePositive(properties.readTimeout(), "readTimeout");
        if (properties.candidateLimit() <= 0 || properties.candidateLimit() > 100) {
            throw new IllegalArgumentException("candidateLimit must be between 1 and 100");
        }
        if (!Double.isFinite(properties.minSimilarity())
                || properties.minSimilarity() < 0.0
                || properties.minSimilarity() > 1.0) {
            throw new IllegalArgumentException("minSimilarity must be between 0 and 1");
        }
        return properties;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank when vector search is enabled");
        }
    }

    private static void requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be greater than zero");
        }
    }

    private record IndexedEmbedding(int index, EmbeddingVector embedding) {
    }

    static class TextEmbeddingException extends RuntimeException {
        TextEmbeddingException(String message) {
            super(message);
        }

        TextEmbeddingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
