package dev.mcpcompass.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registry.cron")
public record RegistryCronProperties(boolean enabled, String secret, int maxPagesPerRun) {
}
