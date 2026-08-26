package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DockerCliContainerRunnerIntegrationTest {
    @Test
    void materializesOnlyThePersistedManifestWhenUnexpectedNpmLogsAreAdjacent() throws Exception {
        Path fixture = Path.of("target", "container-manifest-handoff", UUID.randomUUID().toString())
                .toAbsolutePath();
        Files.createDirectories(fixture);
        Path manifest = fixture.resolve("container-input-manifest.json");
        ObjectMapper objectMapper = new ObjectMapper();
        Files.writeString(manifest, objectMapper.writeValueAsString(Map.of(
                "files", List.of(
                        Map.of("path", "package.json", "content", "{}"),
                        Map.of("path", "src/index.ts", "content", "console.log('ready');")
                )
        )));
        Files.writeString(fixture.resolve("npm-unexpected.log"), "test-only verifier output");

        try (GeneratedProjectWorkspace workspace = materializeContainerInput(
                manifest, fixture.resolve("workspaces"), objectMapper
        )) {
            assertThat(workspace.directory().resolve("package.json")).isRegularFile();
            assertThat(workspace.directory().resolve("src/index.ts")).isRegularFile();
            assertThat(findNpmLogs(workspace.directory())).isEmpty();
        } finally {
            deleteRecursively(fixture);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MCP_COMPASS_VERIFY_CONTAINER_EXECUTION", matches = "true")
    void validatesExactGeneratedProjectWithInspectorAndLeavesNoContainerBehind() throws Exception {
        Path manifest = findGeneratedManifest();
        assertThat(findNpmLogs(manifest.getParent()))
                .as("backend verifier should contain test-only npm logs outside the persisted manifest")
                .isNotEmpty();
        ObjectMapper objectMapper = new ObjectMapper();
        try (GeneratedProjectWorkspace workspace = materializeContainerInput(
                manifest, Path.of("target", "container-integration").toAbsolutePath(), objectMapper
        )) {
            assertThat(findNpmLogs(workspace.directory())).isEmpty();
            ContainerExecutionResult result = new DockerCliContainerRunner("docker").execute(
                    ContainerExecutionRequest.generatedProject(
                            workspace.directory(),
                            "mcp-compass/typescript-sandbox:1.0",
                            Duration.ofSeconds(10),
                            new ContainerSandboxPolicy(
                                    "65532:65532", "0.5", 256, 64, Duration.ofSeconds(30), "none"
                            )
                    )
            );

            assertThat(result.completed())
                    .withFailMessage("Generated protocol validation failed: %s", result.failureSummary())
                    .isTrue();
            assertThat(result.output()).contains("\"tools\"");
            assertThat(listValidationContainers()).isBlank();
        }
    }

    private static Path findGeneratedManifest() {
        Path manifest = Path.of(
                "..", "backend", "target", "generated-project-verification", "container-input-manifest.json"
        ).normalize();
        if (!Files.isRegularFile(manifest)) {
            throw new IllegalStateException("Backend generated-project verification did not emit its manifest");
        }
        return manifest;
    }

    private static GeneratedProjectWorkspace materializeContainerInput(
            Path manifest,
            Path workspaceRoot,
            ObjectMapper objectMapper
    ) throws Exception {
        return GeneratedProjectWorkspace.materialize(workspaceRoot, Files.readString(manifest), objectMapper);
    }

    private static List<Path> findNpmLogs(Path root) throws Exception {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("npm-.*\\.log"))
                    .toList();
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
