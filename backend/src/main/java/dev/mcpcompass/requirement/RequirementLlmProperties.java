package dev.mcpcompass.requirement;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.requirement.llm")
public record RequirementLlmProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int maxOutputTokens
) {
}
