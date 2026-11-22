package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StdioInstallabilityPilotTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void keepsEveryObservedOutcomeDistinctAndInfrastructureUnknown() {
        for (var status : StdioInstallabilityPilot.Status.values()) {
            String report = "{\"status\":\"" + status + "\",\"runtimeMs\":1,\"startedAt\":\"2026-11-12T00:00:00Z\"}";
            assertThat(StdioInstallabilityPilot.report(ContainerExecutionResult.completed(report), mapper))
                    .contains(status.name());
        }
        for (var result : new ContainerExecutionResult[]{ContainerExecutionResult.exited(125, "daemon unavailable"),
                ContainerExecutionResult.timedOut(""), ContainerExecutionResult.completed("bad json"),
                ContainerExecutionResult.completed("{\"status\":\"INSTALLABLE\"}")}) {
            assertThat(StdioInstallabilityPilot.report(result, mapper)).contains("NOT_TESTED");
        }
    }

    @Test
    void usesExistingSandboxWithoutNetworkMountsOrShellAndOnlyAllowsFixedSamples() {
        var request = StdioInstallabilityPilot.request("memory");
        var command = new DockerContainerCommandFactory("docker").create("pilot-test", request);
        assertThat(command).containsSubsequence("--network", "none");
        assertThat(command).containsSubsequence("--user", "65532:65532");
        assertThat(command).contains("--read-only", "--cap-drop", "--pids-limit");
        assertThat(command).doesNotContain("--mount", "sh", "-c", "--privileged");
        assertThat(request.expectedOutcome()).isEqualTo(ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT);
        assertThat(request.observationWindow()).isEqualTo(Duration.ofSeconds(20));
        assertThatThrownBy(() -> StdioInstallabilityPilot.request("../../arbitrary")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MCP_COMPASS_VERIFY_CONTAINER_EXECUTION", matches = "true")
    void measuresPinnedSupportedPackagesInsideExistingSandbox() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path output = Path.of("target", "stdio-installability-pilot");
        Files.createDirectories(output);
        Path buildLog = output.resolve("image-build.log");
        long started = System.nanoTime();
        Process build = new ProcessBuilder("docker", "build", "-f",
                "validation-worker/runtime/stdio-pilot/Dockerfile", "-t", StdioInstallabilityPilot.IMAGE, ".")
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(buildLog.toFile()).start();
        try {
            assertThat(build.waitFor(240, TimeUnit.SECONDS)).as("Pilot image preparation deadline").isTrue();
            assertThat(build.exitValue()).withFailMessage("Pilot image build failed: %s", Files.readString(buildLog)).isZero();
        } finally {
            if (build.isAlive()) build.destroyForcibly();
        }
        System.out.println("EVI-11 image preparation milliseconds=" + Duration.ofNanos(System.nanoTime() - started).toMillis());
        var expectations = new java.util.LinkedHashMap<>(StdioInstallabilityPilot.CONTROLS);
        StdioInstallabilityPilot.SAMPLES.forEach(sample -> expectations.put(sample, "INSTALLABLE"));
        for (var expectation : expectations.entrySet()) {
            String sample = expectation.getKey();
            var result = new DockerCliContainerRunner("docker").execute(StdioInstallabilityPilot.request(sample));
            String report = StdioInstallabilityPilot.report(result, mapper);
            Files.writeString(output.resolve(sample + ".json"), report);
            System.out.println("EVI-11 " + sample + " " + report);
            // The selected credential-free supported sample must actually be exercised in CI.
            assertThat(mapper.readTree(report).path("status").asText()).isEqualTo(expectation.getValue());
        }
    }
}
