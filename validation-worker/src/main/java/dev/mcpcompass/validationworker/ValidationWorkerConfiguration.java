package dev.mcpcompass.validationworker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

record ValidationWorkerConfiguration(
        String databaseUrl,
        String databaseUsername,
        String databasePassword,
        String dockerCli,
        String generatedImage,
        Path workspaceRoot,
        Duration startupWindow,
        Duration pollInterval
) {
    static ValidationWorkerConfiguration from(Map<String, String> environment) {
        return new ValidationWorkerConfiguration(
                environment.getOrDefault("VALIDATION_DATABASE_URL", "jdbc:postgresql://localhost:5432/mcp_compass"),
                environment.getOrDefault("VALIDATION_DATABASE_USERNAME", "mcp_compass"),
                environment.getOrDefault("VALIDATION_DATABASE_PASSWORD", "mcp_compass"),
                environment.getOrDefault("VALIDATION_DOCKER_CLI", "docker"),
                environment.getOrDefault("VALIDATION_GENERATED_IMAGE", "mcp-compass/typescript-sandbox:1.0"),
                Path.of(environment.getOrDefault(
                        "VALIDATION_WORKSPACE_ROOT",
                        Path.of(System.getProperty("java.io.tmpdir"), "mcp-compass-validation").toString()
                )).toAbsolutePath(),
                seconds(environment, "VALIDATION_STARTUP_WINDOW_SECONDS", 5, 1, 300),
                seconds(environment, "VALIDATION_POLL_INTERVAL_SECONDS", 5, 1, 60)
        );
    }

    private static Duration seconds(
            Map<String, String> environment,
            String key,
            long defaultValue,
            long minimum,
            long maximum
    ) {
        long value = Long.parseLong(environment.getOrDefault(key, Long.toString(defaultValue)));
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return Duration.ofSeconds(value);
    }
}
