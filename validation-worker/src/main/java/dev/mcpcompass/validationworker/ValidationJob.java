package dev.mcpcompass.validationworker;

import java.util.UUID;

record ValidationJob(UUID id, String projectName, String projectManifest) {
}
