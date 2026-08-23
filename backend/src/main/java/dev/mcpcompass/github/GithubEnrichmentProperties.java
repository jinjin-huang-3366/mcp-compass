package dev.mcpcompass.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.github.enrichment")
public record GithubEnrichmentProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout
) {
}
