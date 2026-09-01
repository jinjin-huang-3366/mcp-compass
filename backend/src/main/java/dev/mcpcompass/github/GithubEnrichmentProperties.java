package dev.mcpcompass.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.github.enrichment")
public record GithubEnrichmentProperties(
        boolean enabled,
        String baseUrl,
        String token,
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxArtifactBytes,
        Integer maxStaticTools,
        Integer maxToolSchemaBytes,
        List<String> staticMetadataPaths
) {
    int effectiveMaxArtifactBytes() {
        return maxArtifactBytes == null ? 262_144 : Math.clamp(maxArtifactBytes, 1_024, 1_048_576);
    }

    int effectiveMaxStaticTools() {
        return maxStaticTools == null ? 100 : Math.clamp(maxStaticTools, 1, 500);
    }

    int effectiveMaxToolSchemaBytes() {
        return maxToolSchemaBytes == null ? 65_536 : Math.clamp(maxToolSchemaBytes, 1_024, 262_144);
    }

    List<String> boundedStaticMetadataPaths() {
        List<String> configured = staticMetadataPaths == null
                ? List.of(".mcp/server.json", "mcp-server.json")
                : staticMetadataPaths;
        return configured.stream()
                .filter(GithubEnrichmentProperties::safeRepositoryPath)
                .distinct()
                .limit(5)
                .toList();
    }

    private static boolean safeRepositoryPath(String path) {
        return path != null && !path.isBlank() && path.length() <= 256
                && !path.startsWith("/") && !path.contains("\\") && !path.contains("..")
                && path.endsWith(".json");
    }
}
