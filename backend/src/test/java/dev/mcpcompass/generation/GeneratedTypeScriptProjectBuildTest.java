package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "MCP_COMPASS_VERIFY_GENERATED_PROJECT", matches = "true")
class GeneratedTypeScriptProjectBuildTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(3);

    @Test
    void installsCompilesAndTestsTheExactGeneratedManifest() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        GeneratedTypeScriptProject project = new TypeScriptMcpProjectGenerator(
                objectMapper, new TypeScriptMcpRuntimePack()).generate(approvedContract(objectMapper));
        Path verificationDirectory = buildDirectory().resolve("generated-project-verification");
        Files.createDirectories(verificationDirectory);
        Files.writeString(
                verificationDirectory.resolve("container-input-manifest.json"),
                objectMapper.writeValueAsString(project),
                StandardCharsets.UTF_8
        );
        Path projectDirectory = verificationDirectory
                .resolve(UUID.randomUUID().toString())
                .resolve(project.projectName());
        materialize(project, projectDirectory);

        run(projectDirectory, "ci", "--ignore-scripts", "--no-audit", "--no-fund");
        run(projectDirectory, "test");

        assertThat(projectDirectory.resolve("build/index.js")).isRegularFile();
        assertThat(projectDirectory.resolve("build/api-client.test.js")).isRegularFile();
    }

    private static Path buildDirectory() throws Exception {
        Path testClasses = Path.of(GeneratedTypeScriptProjectBuildTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        return testClasses.getParent();
    }

    private static void materialize(GeneratedTypeScriptProject project, Path projectDirectory) throws IOException {
        Path normalizedRoot = projectDirectory.toAbsolutePath().normalize();
        for (GeneratedTypeScriptProject.File file : project.files()) {
            Path target = normalizedRoot.resolve(file.path()).normalize();
            assertThat(target.startsWith(normalizedRoot))
                    .as("generated path stays inside the temporary project: %s", file.path())
                    .isTrue();
            Files.createDirectories(target.getParent());
            Files.writeString(target, file.content(), StandardCharsets.UTF_8);
        }
    }

    private static void run(Path projectDirectory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(npmExecutable());
        command.addAll(List.of(arguments));
        Path output = Files.createTempFile(projectDirectory, "npm-", ".log");
        Process process = new ProcessBuilder(command)
                .directory(projectDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String commandOutput = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(finished)
                .withFailMessage("Generated-project command timed out: %s%n%s", command, commandOutput)
                .isTrue();
        assertThat(process.exitValue())
                .withFailMessage("Generated-project command failed: %s%n%s", command, commandOutput)
                .isZero();
    }

    private static String npmExecutable() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "npm.cmd" : "npm";
    }

    private static McpToolContract approvedContract(ObjectMapper objectMapper) {
        var input = objectMapper.createObjectNode().put("type", "object");
        input.putObject("properties").putObject("petId").put("type", "string");
        input.putArray("required").add("petId");
        input.put("additionalProperties", false);
        var output = objectMapper.createObjectNode().put("type", "object");
        output.putObject("properties").putObject("name").put("type", "string");
        return new McpToolContract(
                "1.0", "APPROVED",
                new McpToolContract.Source("FILE", "petstore.yaml", "3.1.0", "Pet Store", "1.0.0"),
                List.of(new McpToolContract.Tool(
                        "find_pets", "Find an available pet", input, output,
                        new McpToolContract.Operation("GET", "/pets/{petId}", "getPet"),
                        List.of("apiKey"), McpToolContract.Risk.READ_ONLY
                ))
        );
    }
}
