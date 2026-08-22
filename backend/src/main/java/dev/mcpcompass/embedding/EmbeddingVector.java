package dev.mcpcompass.embedding;

import java.util.List;
import java.util.Objects;

public record EmbeddingVector(String model, List<Double> values) {
    public EmbeddingVector {
        model = Objects.requireNonNull(model, "model must not be null").trim();
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        if (model.isEmpty()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (values.size() != EmbeddingProperties.DIMENSIONS) {
            throw new IllegalArgumentException(
                    "embedding must contain exactly " + EmbeddingProperties.DIMENSIONS + " values"
            );
        }
        if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("embedding values must be finite");
        }
    }

    public String postgresLiteral() {
        return values.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }
}
