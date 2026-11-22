package dev.mcpcompass.validationworker;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Research-only runner: results never update Registry/search/product status. */
final class StdioInstallabilityPilot {
    static final String IMAGE = "mcp-compass/stdio-pilot:1";
    static final List<String> SAMPLES = List.of("memory", "filesystem");
    static final Map<String, String> CONTROLS = Map.of("missing-release", "MISSING_OR_YANKED_PACKAGE",
            "process-exit-control", "PROCESS_EXIT", "protocol-error-control", "PROTOCOL_ERROR");
    static final ContainerSandboxPolicy POLICY = new ContainerSandboxPolicy(
            "65532:65532", "0.5", 256, 64, Duration.ofSeconds(30), "none");

    private StdioInstallabilityPilot() { }

    static ContainerExecutionRequest request(String sample) {
        if (!SAMPLES.contains(sample) && !CONTROLS.containsKey(sample))
            throw new IllegalArgumentException("Unsupported pilot sample");
        return new ContainerExecutionRequest(ContainerExecutionRequest.WorkloadType.DISCOVERED_IMAGE,
                IMAGE, List.of("node", "/opt/stdio-pilot/probe.mjs", sample), null,
                Duration.ofSeconds(20), ContainerExecutionRequest.ExpectedOutcome.SUCCESSFUL_EXIT, POLICY);
    }

    static String report(ContainerExecutionResult result, ObjectMapper mapper) {
        // Runtime infrastructure failures do not prove anything about a package.
        if (!result.completed()) return mapper.writeValueAsString(Map.of(
                "status", "NOT_TESTED", "detail", "Sandbox did not return a completed probe",
                "containerOutcome", result.outcome().name()));
        try {
            var json = mapper.readTree(result.output());
            Status.valueOf(json.path("status").asText());
            if (!json.path("runtimeMs").isNumber() || !json.path("startedAt").isTextual())
                throw new IllegalArgumentException("Missing run conditions");
            return mapper.writeValueAsString(json);
        } catch (Exception error) {
            return mapper.writeValueAsString(Map.of("status", "NOT_TESTED",
                    "detail", "Malformed pilot report; package status unknown"));
        }
    }

    enum Status { INSTALLABLE, MISSING_OR_YANKED_PACKAGE, PROCESS_EXIT, PROTOCOL_ERROR, NOT_TESTED }
}
