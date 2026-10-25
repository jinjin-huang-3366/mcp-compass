package dev.mcpcompass.validation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
class ValidationWorkerAvailability {
    private static final Duration HEARTBEAT_MAX_AGE = Duration.ofSeconds(30);
    private final JdbcTemplate jdbcTemplate;

    ValidationWorkerAvailability(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    ValidationJobResponse describe(ValidationJobResponse job) {
        Instant lastSeen = jdbcTemplate.query(
                "SELECT last_seen_at FROM validation_worker_heartbeat WHERE id = 1",
                result -> result.next() ? result.getTimestamp(1).toInstant() : null
        );
        return describe(job, lastSeen, Instant.now());
    }

    static ValidationJobResponse describe(ValidationJobResponse job, Instant lastSeen, Instant now) {
        boolean available = lastSeen != null && !lastSeen.isBefore(now.minus(HEARTBEAT_MAX_AGE));
        String message = switch (job.status()) {
            case QUEUED -> available
                    ? "Queued for the separate validation worker; no execution result exists yet."
                    : "No recent validation worker heartbeat. This job is queued, not executed; ask the operator to start or restore the isolated worker.";
            case RUNNING -> available
                    ? "The separate validation worker has claimed this job; no completed result exists yet."
                    : "The worker heartbeat is stale while this job is RUNNING. Check the worker and job logs; execution outcome is unknown.";
            case EXECUTED -> "The isolated worker completed protocol validation; review the bounded report and its limitations.";
            case FAILED -> "Isolated validation failed; inspect failureReason and worker logs.";
        };
        return new ValidationJobResponse(
                job.id(), job.status(), job.projectName(), job.queuedAt(), job.startedAt(), job.finishedAt(),
                job.failureReason(), job.protocolResult(), job.securityReport(),
                available ? "AVAILABLE" : "UNAVAILABLE", lastSeen, message
        );
    }
}
