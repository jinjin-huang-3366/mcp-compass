package dev.mcpcompass.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.registry")
public record RegistryProperties(
        String baseUrl,
        int pageSize,
        Duration connectTimeout,
        Duration readTimeout,
        Sync sync
) {
    public record Sync(boolean enabled, long fixedDelayMs, int maxPagesPerRun) {
    }
}
