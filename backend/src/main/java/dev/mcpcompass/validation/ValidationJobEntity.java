package dev.mcpcompass.validation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "validation_job")
class ValidationJobEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ValidationJobStatus status;

    @Column(name = "project_name", nullable = false, length = 255)
    private String projectName;

    @Column(name = "generator_version", nullable = false, length = 32)
    private String generatorVersion;

    @Column(name = "contract_version", nullable = false, length = 32)
    private String contractVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "project_manifest", nullable = false, columnDefinition = "jsonb")
    private String projectManifest;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "protocol_result", columnDefinition = "jsonb")
    private String protocolResult;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_report", columnDefinition = "jsonb")
    private String securityReport;

    protected ValidationJobEntity() {
    }

    static ValidationJobEntity queued(
            UUID id,
            String projectName,
            String generatorVersion,
            String contractVersion,
            String projectManifest,
            Instant queuedAt
    ) {
        ValidationJobEntity job = new ValidationJobEntity();
        job.id = id;
        job.status = ValidationJobStatus.QUEUED;
        job.projectName = projectName;
        job.generatorVersion = generatorVersion;
        job.contractVersion = contractVersion;
        job.projectManifest = projectManifest;
        job.queuedAt = queuedAt;
        return job;
    }

    UUID id() {
        return id;
    }

    ValidationJobStatus status() {
        return status;
    }

    String projectName() {
        return projectName;
    }

    String generatorVersion() {
        return generatorVersion;
    }

    String contractVersion() {
        return contractVersion;
    }

    String projectManifest() {
        return projectManifest;
    }

    Instant queuedAt() {
        return queuedAt;
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    String failureReason() {
        return failureReason;
    }

    String protocolResult() {
        return protocolResult;
    }

    String securityReport() {
        return securityReport;
    }
}
