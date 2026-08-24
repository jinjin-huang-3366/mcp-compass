package dev.mcpcompass.validationworker;

import java.util.Optional;
import java.util.UUID;

interface ValidationJobStore {
    Optional<ValidationJob> claimNext() throws Exception;

    void markExecuted(UUID id) throws Exception;

    void markFailed(UUID id, String reason) throws Exception;
}
