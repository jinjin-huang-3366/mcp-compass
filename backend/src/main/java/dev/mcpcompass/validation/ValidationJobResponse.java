package dev.mcpcompass.validation;

import java.time.Instant;
import java.util.UUID;

record ValidationJobResponse(
        UUID id,
        ValidationJobStatus status,
        String projectName,
        Instant queuedAt
) {
    static ValidationJobResponse from(ValidationJobEntity job) {
        return new ValidationJobResponse(job.id(), job.status(), job.projectName(), job.queuedAt());
    }
}
