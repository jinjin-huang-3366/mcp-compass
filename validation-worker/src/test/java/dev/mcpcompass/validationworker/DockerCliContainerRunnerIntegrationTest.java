package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_COMPASS_VERIFY_CONTAINER_EXECUTION", matches = "true")
class DockerCliContainerRunnerIntegrationTest {
    @Test
    void startsExactGeneratedProjectInContainerAndLeavesNoContainerBehind() throws Exception {
        Path source = findGeneratedProject();
        Path workspace = Path.of("target", "container-integration", UUID.randomUUID().toString())
                .toAbsolutePath();
        copyProjectWithoutHostDependencies(source, workspace);
        try {
            ContainerExecutionResult result = new DockerCliContainerRunner("docker").execute(
                    ContainerExecutionRequest.generatedProject(
                            workspace,
                            "mcp-compass/typescript-sandbox:1.0",
                            Duration.ofSeconds(10)
                    )
            );

            assertThat(result.observedRunning())
                    .withFailMessage("Generated container exited early: %s", result.failureSummary())
                    .isTrue();
            assertThat(listValidationContainers()).isBlank();
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static Path findGeneratedProject() throws Exception {
        Path root = Path.of("..", "backend", "target", "generated-project-verification").normalize();
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> Files.isDirectory(path) && path.getFileName().toString().endsWith("-mcp-server"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Backend generated-project verification did not run"));
        }
    }

    private static void copyProjectWithoutHostDependencies(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                if (relative.getNameCount() > 0
                        && List.of("node_modules", "build").contains(relative.getName(0).toString())) {
                    continue;
                }
                Path destination = target.resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static String listValidationContainers() throws Exception {
        Process process = new ProcessBuilder(
                "docker", "ps", "--all",
                "--filter", "label=dev.mcpcompass.validation=ephemeral",
                "--format", "{{.ID}}"
        ).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).isZero();
        return output;
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
