package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DockerContainerCommandFactoryTest {
    private final DockerContainerCommandFactory commands = new DockerContainerCommandFactory("docker");

    @Test
    void generatedProjectIsMountedOnlyInsideAnEphemeralLockedDownContainer() throws Exception {
        Path workspace = Files.createDirectories(Path.of("target", "command-test-workspace")).toAbsolutePath();
        ContainerExecutionRequest request = ContainerExecutionRequest.generatedProject(
                workspace, "mcp-compass/typescript-sandbox:1.0", Duration.ofSeconds(5), defaultPolicy()
        );

        List<String> command = commands.create("job-1", request);

        assertThat(command).containsSubsequence(
                "docker", "create", "--rm", "--interactive", "--name", "job-1",
                "--label", "dev.mcpcompass.validation=ephemeral",
                "--network", "none",
                "--user", "65532:65532",
                "--cpus", "0.5",
                "--memory", "256m",
                "--pids-limit", "64",
                "--env", "HOME=/tmp",
                "--env", "npm_config_cache=/tmp/npm-cache",
                "--read-only",
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=64m,uid=65532,gid=65532,mode=0700",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges"
        );
        assertThat(command).contains(
                "/workspace:rw,noexec,nosuid,size=64m,uid=65532,gid=65532,mode=0700",
                "type=bind,source=" + workspace.toAbsolutePath() + ",target=/input,readonly",
                "mcp-compass/typescript-sandbox:1.0",
                "cp -R /input/. /workspace/"
                        + " && ln -s /opt/mcp-compass/runtime/node_modules node_modules"
                        + " && npm run --silent build"
                        + " && exec /opt/mcp-compass/runtime/node_modules/.bin/mcp-inspector"
                        + " --cli node build/index.js --method tools/list --format json"
        );
        assertThat(request.expectedOutcome())
                .isEqualTo(ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT);
        assertThat(commands.startAttached("job-1"))
                .containsExactly("docker", "start", "--attach", "--interactive", "job-1");
    }

    @Test
    void discoveredImageUsesItsOwnFilesystemAndReceivesArgumentsWithoutAHostShell() {
        ContainerExecutionRequest request = ContainerExecutionRequest.discoveredImage(
                "ghcr.io/example/mcp:1.2.3",
                List.of("node", "server.js", "--stdio"),
                Duration.ofSeconds(5),
                defaultPolicy()
        );

        List<String> command = commands.create("job-2", request);

        assertThat(command).doesNotContain("--mount", "--workdir");
        assertThat(command).endsWith(
                "ghcr.io/example/mcp:1.2.3", "node", "server.js", "--stdio"
        );
        assertThat(request.expectedOutcome())
                .isEqualTo(ContainerExecutionRequest.ExpectedOutcome.RUNNING_AFTER_WINDOW);
    }

    private static ContainerSandboxPolicy defaultPolicy() {
        return new ContainerSandboxPolicy(
                "65532:65532", "0.5", 256, 64, Duration.ofSeconds(30), "none"
        );
    }
}
