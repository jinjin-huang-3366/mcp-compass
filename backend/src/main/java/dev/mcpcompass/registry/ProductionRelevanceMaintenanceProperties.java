package dev.mcpcompass.registry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.production-relevance.maintenance")
public record ProductionRelevanceMaintenanceProperties(boolean enabled, String secret) {
}
