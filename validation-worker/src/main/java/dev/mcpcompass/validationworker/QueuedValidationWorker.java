package dev.mcpcompass.validationworker;

import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

final class QueuedValidationWorker {
    private final ValidationJobStore jobs;
    private final IsolatedContainerRunner containers;
    private final ObjectMapper objectMapper;
    private final Path workspaceRoot;
    private final String generatedImage;
    private final Duration protocolTimeout;

    QueuedValidationWorker(
            ValidationJobStore jobs,
            IsolatedContainerRunner containers,
            ObjectMapper objectMapper,
            Path workspaceRoot,
            String generatedImage,
            Duration protocolTimeout
    ) {
        this.jobs = jobs;
        this.containers = containers;
        this.objectMapper = objectMapper;
        this.workspaceRoot = workspaceRoot;
        this.generatedImage = generatedImage;
        this.protocolTimeout = protocolTimeout;
    }

    boolean runNext() throws Exception {
        Optional<ValidationJob> claimed = jobs.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        ValidationJob job = claimed.orElseThrow();
        try (GeneratedProjectWorkspace workspace = GeneratedProjectWorkspace.materialize(
                workspaceRoot, job.projectManifest(), objectMapper
        )) {
            ContainerExecutionResult result = containers.execute(ContainerExecutionRequest.generatedProject(
                    workspace.directory(), generatedImage, protocolTimeout
            ));
            if (result.completed()) {
                String protocolResult = McpInspectorProtocolResult.validateAndSerialize(result.output(), objectMapper);
                jobs.markExecuted(job.id(), protocolResult);
                System.out.printf("Validation job %s passed isolated MCP Inspector protocol validation%n", job.id());
            } else {
                jobs.markFailed(job.id(), result.failureSummary());
                System.err.printf("Validation job %s failed isolated MCP Inspector protocol validation%n", job.id());
            }
        } catch (Exception error) {
            try {
                jobs.markFailed(job.id(), error.getClass().getSimpleName() + ": " + error.getMessage());
            } catch (Exception persistenceError) {
                error.addSuppressed(persistenceError);
            }
            throw error;
        }
        return true;
    }
}
