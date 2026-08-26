package dev.mcpcompass.validationworker;

import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

public final class ValidationWorkerApplication {
    private ValidationWorkerApplication() {
    }

    public static void main(String[] args) throws Exception {
        ValidationWorkerConfiguration configuration = ValidationWorkerConfiguration.from(System.getenv());
        DockerCliContainerRunner containers = new DockerCliContainerRunner(configuration.dockerCli());
        if (args.length > 0 && "discovered".equals(args[0])) {
            runDiscovered(args, containers, configuration);
            return;
        }

        QueuedValidationWorker worker = new QueuedValidationWorker(
                new JdbcValidationJobStore(
                        configuration.databaseUrl(),
                        configuration.databaseUsername(),
                        configuration.databasePassword()
                ),
                containers,
                new ObjectMapper(),
                configuration.workspaceRoot(),
                configuration.generatedImage(),
                configuration.protocolTimeout(),
                configuration.sandboxPolicy()
        );
        if (args.length > 0 && "queue-once".equals(args[0])) {
            worker.runNext();
            return;
        }
        if (args.length > 0 && !"queue".equals(args[0])) {
            throw new IllegalArgumentException("Usage: queue | queue-once | discovered <image> [command ...]");
        }
        while (!Thread.currentThread().isInterrupted()) {
            if (!worker.runNext()) {
                Thread.sleep(configuration.pollInterval());
            }
        }
    }

    private static void runDiscovered(
            String[] args,
            IsolatedContainerRunner containers,
            ValidationWorkerConfiguration configuration
    ) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: discovered <image> [command ...]");
        }
        List<String> command = args.length == 2
                ? List.of()
                : Arrays.asList(args).subList(2, args.length);
        ContainerExecutionResult result = containers.execute(ContainerExecutionRequest.discoveredImage(
                args[1], command, configuration.startupWindow(), configuration.sandboxPolicy()
        ));
        if (!result.observedRunning()) {
            throw new IllegalStateException(result.failureSummary());
        }
        System.out.println("Discovered MCP server started in an isolated ephemeral container");
    }
}
