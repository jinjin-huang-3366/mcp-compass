package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueuedValidationWorkerTest {
    @Test
    void marksJobExecutedOnlyAfterInspectorReturnsAValidToolsList() throws Exception {
        RecordingJobStore jobs = new RecordingJobStore(job());
        RecordingRunner containers = new RecordingRunner(ContainerExecutionResult.completed("""
                {"result":{"tools":[{"name":"find_pets","description":"Find available pets"}]}}
                """));
        QueuedValidationWorker worker = worker(jobs, containers);

        assertThat(worker.runNext()).isTrue();

        assertThat(jobs.executed).isTrue();
        assertThat(jobs.failedReason).isNull();
        assertThat(jobs.protocolResult)
                .contains("\"validator\":\"mcp-inspector\"")
                .contains("\"method\":\"tools/list\"")
                .contains("\"name\":\"find_pets\"");
        assertThat(containers.request.workloadType())
                .isEqualTo(ContainerExecutionRequest.WorkloadType.GENERATED_PROJECT);
        assertThat(containers.request.expectedOutcome())
                .isEqualTo(ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT);
        assertThat(containers.request.workspace()).doesNotExist();
    }

    @Test
    void recordsEarlyContainerExitWithoutClaimingAnotherTask() throws Exception {
        RecordingJobStore jobs = new RecordingJobStore(job());
        QueuedValidationWorker worker = worker(
                jobs,
                new RecordingRunner(ContainerExecutionResult.exited(1, "module load failed"))
        );

        assertThat(worker.runNext()).isTrue();

        assertThat(jobs.executed).isFalse();
        assertThat(jobs.failedReason).contains("exit 1", "module load failed");
    }

    @Test
    void rejectsSuccessfulProcessOutputThatIsNotAnInspectorToolsList() throws Exception {
        RecordingJobStore jobs = new RecordingJobStore(job());
        QueuedValidationWorker worker = worker(
                jobs,
                new RecordingRunner(ContainerExecutionResult.completed("{\"result\":{}}"))
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(worker::runNext)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tools/list");

        assertThat(jobs.executed).isFalse();
        assertThat(jobs.failedReason).contains("MCP Inspector returned no tools/list result");
    }

    private QueuedValidationWorker worker(RecordingJobStore jobs, RecordingRunner containers) {
        return new QueuedValidationWorker(
                jobs,
                containers,
                new ObjectMapper(),
                Path.of("target", "queued-worker-test").toAbsolutePath(),
                "mcp-compass/typescript-sandbox:1.0",
                Duration.ofSeconds(2)
        );
    }

    private static ValidationJob job() {
        return new ValidationJob(
                UUID.randomUUID(),
                "pet-store-mcp-server",
                "{\"files\":[{\"path\":\"package.json\",\"content\":\"{}\"}]}"
        );
    }

    private static final class RecordingJobStore implements ValidationJobStore {
        private Optional<ValidationJob> next;
        private boolean executed;
        private String failedReason;
        private String protocolResult;

        private RecordingJobStore(ValidationJob job) {
            this.next = Optional.of(job);
        }

        @Override
        public Optional<ValidationJob> claimNext() {
            Optional<ValidationJob> result = next;
            next = Optional.empty();
            return result;
        }

        @Override
        public void markExecuted(UUID id, String protocolResult) {
            executed = true;
            this.protocolResult = protocolResult;
        }

        @Override
        public void markFailed(UUID id, String reason) {
            failedReason = reason;
        }
    }

    private static final class RecordingRunner implements IsolatedContainerRunner {
        private final ContainerExecutionResult result;
        private ContainerExecutionRequest request;

        private RecordingRunner(ContainerExecutionResult result) {
            this.result = result;
        }

        @Override
        public ContainerExecutionResult execute(ContainerExecutionRequest request) throws Exception {
            this.request = request;
            assertThat(Files.exists(request.workspace().resolve("package.json"))).isTrue();
            return result;
        }
    }
}
