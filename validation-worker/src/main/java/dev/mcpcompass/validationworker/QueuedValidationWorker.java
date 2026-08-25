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
    private final Duration startupWindow;
    private final ContainerSandboxPolicy sandboxPolicy;

    QueuedValidationWorker(
            ValidationJobStore jobs,
            IsolatedContainerRunner containers,
            ObjectMapper objectMapper,
            Path workspaceRoot,
            String generatedImage,
            Duration startupWindow,
            ContainerSandboxPolicy sandboxPolicy
    ) {
        this.jobs = jobs;
        this.containers = containers;
        this.objectMapper = objectMapper;
        this.workspaceRoot = workspaceRoot;
        this.generatedImage = generatedImage;
        this.startupWindow = startupWindow;
        this.sandboxPolicy = sandboxPolicy;
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
                    workspace.directory(), generatedImage, startupWindow, sandboxPolicy
            ));
            if (result.observedRunning()) {
                jobs.markExecuted(job.id());
                System.out.printf("Validation job %s started in an isolated ephemeral container%n", job.id());
            } else {
                jobs.markFailed(job.id(), result.failureSummary());
                System.err.printf("Validation job %s failed isolated startup%n", job.id());
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
