package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ForbiddenCapabilityPilotTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "MCP_COMPASS_VERIFY_CONTAINER_EXECUTION", matches = "true")
    void observesPostgresDenyTheSameStdioToolCallThatTheWriterCanComplete() throws Exception {
        String image = "mcp-compass/forbidden-capability-pilot:1";
        Path output = Path.of("target", "forbidden-capability-pilot");
        Files.createDirectories(output);
        Path buildLog = output.resolve("image-build.log");
        Process build = new ProcessBuilder("docker", "build", "-f",
                "validation-worker/runtime/forbidden-capability-pilot/Dockerfile", "-t", image, ".")
                .directory(Path.of("..").toFile()).redirectErrorStream(true).redirectOutput(buildLog.toFile()).start();
        try {
            assertThat(build.waitFor(240, TimeUnit.SECONDS)).as("Fixture image build deadline").isTrue();
            assertThat(build.exitValue()).withFailMessage("Fixture image build failed: %s", Files.readString(buildLog))
                    .isZero();
        } finally {
            if (build.isAlive()) build.destroyForcibly();
        }

        var request = new ContainerExecutionRequest(ContainerExecutionRequest.WorkloadType.DISCOVERED_IMAGE,
                image, List.of("python", "/opt/forbidden-capability-pilot/probe.py"), null, Duration.ofSeconds(30),
                ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT,
                new ContainerSandboxPolicy("65532:65532", "0.5", 256, 64, Duration.ofSeconds(40), "none"));
        var result = new DockerCliContainerRunner("docker").execute(request);
        assertThat(result.completed()).withFailMessage("Fixture execution failed: %s", result.failureSummary()).isTrue();

        var mapper = new ObjectMapper();
        var report = mapper.readTree(result.output());
        Files.writeString(output.resolve("observation.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        System.out.println("EVI-03 " + result.output().strip());
        assertThat(report.path("transport").asText()).isEqualTo("stdio");
        assertThat(report.path("uid").asInt()).isEqualTo(65532);
        assertThat(report.path("gid").asInt()).isEqualTo(65532);
        assertThat(report.path("writeCall")).isEqualTo(mapper.readTree("""
                {"name":"rename_item","arguments":{"name":"after"}}
                """));
        assertThat(report.path("readerRead")).isEqualTo(mapper.readTree("""
                {"role":"fixture_reader","name":"before","isError":false}
                """));
        assertThat(report.path("readerWrite")).isEqualTo(mapper.readTree("""
                {"sqlstate":"42501","isError":true}
                """));
        assertThat(report.path("writerWrite")).isEqualTo(mapper.readTree("""
                {"role":"fixture_writer","name":"after","isError":false}
                """));
        assertThat(report.path("invalidCredentialRead")).isEqualTo(mapper.readTree("""
                {"reason":"DATABASE_CALL_FAILED","isError":true}
                """));
        assertThat(report.path("invalidArgumentWrite")).isEqualTo(mapper.readTree("""
                {"rpcError":-32602}
                """));
        assertThat(report.path("databaseState")).isEqualTo(mapper.readTree("""
                {"before":"before","afterReader":"before","afterWriter":"after"}
                """));
    }
}
