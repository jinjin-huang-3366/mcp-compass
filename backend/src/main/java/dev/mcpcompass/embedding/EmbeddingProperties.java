package dev.mcpcompass.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.search.vector")
public record EmbeddingProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int candidateLimit,
        double minSimilarity
) {
    public static final int DIMENSIONS = 384;
}
