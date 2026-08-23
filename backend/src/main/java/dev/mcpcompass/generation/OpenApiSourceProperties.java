package dev.mcpcompass.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.generation.openapi-source")
public record OpenApiSourceProperties(long maxBytes, Duration connectTimeout, Duration readTimeout) {
}
