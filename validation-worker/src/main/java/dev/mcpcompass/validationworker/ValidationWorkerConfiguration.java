package dev.mcpcompass.validationworker;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

record ValidationWorkerConfiguration(
        String databaseUrl,
        String databaseUsername,
        String databasePassword,
        String dockerCli,
        String generatedImage,
        Path workspaceRoot,
        Duration startupWindow,
        Duration pollInterval,
        ContainerSandboxPolicy sandboxPolicy
) {
    static ValidationWorkerConfiguration from(Map<String, String> environment) {
        Duration startupWindow = seconds(environment, "VALIDATION_STARTUP_WINDOW_SECONDS", 5, 1, 300);
        Duration wallTimeLimit = seconds(environment, "VALIDATION_WALL_TIME_LIMIT_SECONDS", 30, 1, 900);
        if (startupWindow.compareTo(wallTimeLimit) > 0) {
            throw new IllegalArgumentException(
                    "VALIDATION_STARTUP_WINDOW_SECONDS cannot exceed VALIDATION_WALL_TIME_LIMIT_SECONDS"
            );
        }
        String network = environment.getOrDefault("VALIDATION_NETWORK", "none");
        requireAllowListedNetwork(network, environment.getOrDefault("VALIDATION_ALLOWED_NETWORKS", ""));

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
                startupWindow,
                seconds(environment, "VALIDATION_POLL_INTERVAL_SECONDS", 5, 1, 60),
                new ContainerSandboxPolicy(
                        environment.getOrDefault("VALIDATION_CONTAINER_USER", "65532:65532"),
                        environment.getOrDefault("VALIDATION_CPU_LIMIT", "0.5"),
                        integer(environment, "VALIDATION_MEMORY_LIMIT_MB", 256),
                        integer(environment, "VALIDATION_PROCESS_LIMIT", 64),
                        wallTimeLimit,
                        network
                )
        );
    }

    private static void requireAllowListedNetwork(String network, String configuredAllowList) {
        if ("none".equals(network)) {
            return;
        }
        Set<String> allowedNetworks = Arrays.stream(configuredAllowList.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (!allowedNetworks.contains(network)) {
            throw new IllegalArgumentException(
                    "VALIDATION_NETWORK must be 'none' or appear in VALIDATION_ALLOWED_NETWORKS"
            );
        }
    }

    private static int integer(Map<String, String> environment, String key, int defaultValue) {
        try {
            return Integer.parseInt(environment.getOrDefault(key, Integer.toString(defaultValue)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
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
