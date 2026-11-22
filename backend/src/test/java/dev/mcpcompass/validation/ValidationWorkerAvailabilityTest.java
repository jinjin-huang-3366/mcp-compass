package dev.mcpcompass.validation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationWorkerAvailabilityTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void queuedJobWithNoHeartbeatIsUnexecutedAndActionable() {
        ValidationJobResponse response = ValidationWorkerAvailability.describe(queued(), null, NOW);

        assertThat(response.workerAvailability()).isEqualTo("UNAVAILABLE");
        assertThat(response.workerLastSeenAt()).isNull();
        assertThat(response.status()).isEqualTo(ValidationJobStatus.QUEUED);
        assertThat(response.protocolResult()).isNull();
        assertThat(response.statusMessage()).contains("queued, not executed", "start or restore");
    }

    @Test
    void freshHeartbeatDoesNotClaimQueuedJobHasExecuted() {
        ValidationJobResponse response = ValidationWorkerAvailability.describe(queued(), NOW.minusSeconds(10), NOW);

        assertThat(response.workerAvailability()).isEqualTo("AVAILABLE");
        assertThat(response.workerLastSeenAt()).isEqualTo(NOW.minusSeconds(10));
        assertThat(response.status()).isEqualTo(ValidationJobStatus.QUEUED);
        assertThat(response.statusMessage()).contains("no execution result exists yet");
    }

    @Test
    void staleHeartbeatReturnsUnavailable() {
        ValidationJobResponse response = ValidationWorkerAvailability.describe(queued(), NOW.minusSeconds(31), NOW);

        assertThat(response.workerAvailability()).isEqualTo("UNAVAILABLE");
        assertThat(response.workerLastSeenAt()).isEqualTo(NOW.minusSeconds(31));
    }

    @Test
    void runningJobWithStaleHeartbeatHasUnknownOutcome() {
        ValidationJobResponse running = new ValidationJobResponse(UUID.randomUUID(), ValidationJobStatus.RUNNING,
                "example", NOW.minusSeconds(60), NOW.minusSeconds(40), null, null, null, null,
                null, null, null);

        ValidationJobResponse response = ValidationWorkerAvailability.describe(running, NOW.minusSeconds(31), NOW);

        assertThat(response.workerAvailability()).isEqualTo("UNAVAILABLE");
        assertThat(response.status()).isEqualTo(ValidationJobStatus.RUNNING);
        assertThat(response.statusMessage()).contains("outcome is unknown");
    }

    private static ValidationJobResponse queued() {
        return new ValidationJobResponse(UUID.randomUUID(), ValidationJobStatus.QUEUED, "example",
                NOW, null, null, null, null, null, null, null, null);
    }
}
