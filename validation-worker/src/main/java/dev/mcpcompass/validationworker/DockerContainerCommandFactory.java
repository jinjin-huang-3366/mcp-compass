package dev.mcpcompass.validationworker;

import java.util.ArrayList;
import java.util.List;

final class DockerContainerCommandFactory {
    private final String dockerCli;

    DockerContainerCommandFactory(String dockerCli) {
        if (dockerCli == null || dockerCli.isBlank()) {
            throw new IllegalArgumentException("Docker CLI command cannot be blank");
        }
        this.dockerCli = dockerCli;
    }

    List<String> create(String containerName, ContainerExecutionRequest request) {
        List<String> command = new ArrayList<>(List.of(
                dockerCli,
                "create",
                "--rm",
                "--interactive",
                "--name", containerName,
                "--label", "dev.mcpcompass.validation=ephemeral",
                "--network", request.sandboxPolicy().network(),
                "--user", request.sandboxPolicy().user(),
                "--cpus", request.sandboxPolicy().cpuLimit(),
                "--memory", request.sandboxPolicy().memoryLimit(),
                "--pids-limit", Integer.toString(request.sandboxPolicy().processLimit()),
                "--env", "HOME=/tmp",
                "--env", "npm_config_cache=/tmp/npm-cache",
                "--read-only",
                "--tmpfs", ownedTmpfs("/tmp", request.sandboxPolicy()),
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges"
        ));
        if (request.workloadType() == ContainerExecutionRequest.WorkloadType.GENERATED_PROJECT) {
            command.addAll(List.of(
                    "--tmpfs", ownedTmpfs("/workspace", request.sandboxPolicy()),
                    "--mount",
                    "type=bind,source=" + request.workspace() + ",target=/input,readonly",
                    "--workdir", "/workspace"
            ));
        }
        command.add(request.image());
        command.addAll(request.command());
        return List.copyOf(command);
    }

    private static String ownedTmpfs(String path, ContainerSandboxPolicy policy) {
        return path + ":rw,noexec,nosuid,size=64m,uid=" + policy.userId()
                + ",gid=" + policy.groupId() + ",mode=0700";
    }

    List<String> startAttached(String containerName) {
        return List.of(dockerCli, "start", "--attach", "--interactive", containerName);
    }

    List<String> removeForced(String containerName) {
        return List.of(dockerCli, "rm", "--force", containerName);
    }
}
