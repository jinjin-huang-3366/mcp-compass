package dev.mcpcompass.validationworker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

final class JdbcValidationJobStore implements ValidationJobStore {
    private static final int MAX_FAILURE_REASON = 2_000;
    private static final String CLAIM_SQL = """
            WITH next_job AS (
                SELECT id
                FROM validation_job
                WHERE status = 'QUEUED'
                ORDER BY queued_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE validation_job AS job
            SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP
            FROM next_job
            WHERE job.id = next_job.id
            RETURNING job.id, job.project_name, job.project_manifest::text
            """;

    private final String url;
    private final String username;
    private final String password;

    JdbcValidationJobStore(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    @Override
    public Optional<ValidationJob> claimNext() throws Exception {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(CLAIM_SQL);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return Optional.empty();
            }
            return Optional.of(new ValidationJob(
                    result.getObject("id", UUID.class),
                    result.getString("project_name"),
                    result.getString("project_manifest")
            ));
        }
    }

    @Override
    public void markExecuted(UUID id, String protocolResult, String securityReport) throws Exception {
        updateOutcome(id, "EXECUTED", null, protocolResult, securityReport);
    }

    @Override
    public void markFailed(UUID id, String reason) throws Exception {
        String bounded = reason == null ? "Unknown isolated execution failure" : reason;
        updateOutcome(id, "FAILED", bounded.substring(0, Math.min(bounded.length(), MAX_FAILURE_REASON)), null, null);
    }

    private void updateOutcome(
            UUID id,
            String status,
            String failureReason,
            String protocolResult,
            String securityReport
    ) throws Exception {
        String sql = """
                UPDATE validation_job
                SET status = ?, finished_at = CURRENT_TIMESTAMP, failure_reason = ?,
                    protocol_result = CAST(? AS jsonb), security_report = CAST(? AS jsonb)
                WHERE id = ? AND status = 'RUNNING'
                """;
        try (Connection connection = DriverManager.getConnection(url, username, password);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, failureReason);
            statement.setString(3, protocolResult);
            statement.setString(4, securityReport);
            statement.setObject(5, id);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Validation job is no longer RUNNING: " + id);
            }
        }
    }
}
