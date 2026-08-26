package dev.mcpcompass.validationworker;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

record ContainerExecutionRequest(
        WorkloadType workloadType,
        String image,
        List<String> command,
        Path workspace,
        Duration observationWindow,
        ExpectedOutcome expectedOutcome,
        ContainerSandboxPolicy sandboxPolicy
) {
    private static final Pattern IMAGE_REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/:@-]{0,254}");

    ContainerExecutionRequest {
        Objects.requireNonNull(workloadType, "workloadType");
        if (image == null || !IMAGE_REFERENCE.matcher(image).matches()) {
            throw new IllegalArgumentException("Container image must be a bounded OCI image reference");
        }
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Container command arguments cannot be blank");
        }
        if (observationWindow == null || observationWindow.isNegative() || observationWindow.isZero()
                || observationWindow.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("Observation window must be between one millisecond and five minutes");
        }
        Objects.requireNonNull(expectedOutcome, "expectedOutcome");
        Objects.requireNonNull(sandboxPolicy, "sandboxPolicy");
        if (observationWindow.compareTo(sandboxPolicy.wallTimeLimit()) > 0) {
            throw new IllegalArgumentException("Observation window cannot exceed the workload wall-time limit");
        }
        if (workloadType == WorkloadType.GENERATED_PROJECT) {
            if (workspace == null || !workspace.isAbsolute() || !Files.isDirectory(workspace)) {
                throw new IllegalArgumentException("Generated workloads require an absolute workspace directory");
            }
            workspace = workspace.normalize();
            if (workspace.toString().contains(",")) {
                throw new IllegalArgumentException("Workspace path cannot contain a comma");
            }
        } else if (workspace != null) {
            throw new IllegalArgumentException("Discovered image workloads cannot mount a generated workspace");
        }
    }

    static ContainerExecutionRequest generatedProject(
            Path workspace,
            String image,
            Duration startupWindow,
            ContainerSandboxPolicy sandboxPolicy
    ) {
        return new ContainerExecutionRequest(
                WorkloadType.GENERATED_PROJECT,
                image,
                List.of(
                        "sh",
                        "-lc",
                        "cp -R /input/. /workspace/"
                                + " && ln -s /opt/mcp-compass/runtime/node_modules node_modules"
                                + " && npm run --silent build"
                                + " && exec /opt/mcp-compass/runtime/node_modules/.bin/mcp-inspector"
                                + " --cli node build/index.js --method tools/list --format json"
                ),
                workspace.toAbsolutePath(),
                startupWindow,
                ExpectedOutcome.SUCCESSFUL_EXIT,
                sandboxPolicy
        );
    }

    static ContainerExecutionRequest discoveredImage(
            String image,
            List<String> command,
            Duration startupWindow,
            ContainerSandboxPolicy sandboxPolicy
    ) {
        return new ContainerExecutionRequest(
                WorkloadType.DISCOVERED_IMAGE,
                image,
                command,
                null,
                startupWindow,
                ExpectedOutcome.RUNNING_AFTER_WINDOW,
                sandboxPolicy
        );
    }

    enum WorkloadType {
        GENERATED_PROJECT,
        DISCOVERED_IMAGE
    }

    enum ExpectedOutcome {
        SUCCESSFUL_EXIT,
        RUNNING_AFTER_WINDOW
    }
}
