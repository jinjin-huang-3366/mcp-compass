package dev.mcpcompass.validation;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

record ValidationJobResponse(
        UUID id,
        ValidationJobStatus status,
        String projectName,
        Instant queuedAt,
        Instant startedAt,
        Instant finishedAt,
        String failureReason,
        JsonNode protocolResult,
        JsonNode securityReport
) {
    static ValidationJobResponse from(ValidationJobEntity job, ObjectMapper objectMapper) {
        return new ValidationJobResponse(
                job.id(), job.status(), job.projectName(), job.queuedAt(), job.startedAt(), job.finishedAt(),
                job.failureReason(), json(job.protocolResult(), objectMapper), json(job.securityReport(), objectMapper)
        );
    }

    private static JsonNode json(String value, ObjectMapper objectMapper) {
        return value == null ? null : objectMapper.readTree(value);
    }
}
