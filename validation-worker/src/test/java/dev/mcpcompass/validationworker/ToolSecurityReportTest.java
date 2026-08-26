package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSecurityReportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reportsHighestDeclaredRiskAndSandboxEvidenceWithoutInvokingTools() {
        JsonNode report = objectMapper.readTree(ToolSecurityReport.create(
                manifest("""
                        {"tools":[
                          {"name":"find_pets","risk":"READ_ONLY","sourceOperation":{"method":"GET","path":"/pets"},"authenticationRequirements":[]},
                          {"name":"delete_pet","risk":"DESTRUCTIVE","sourceOperation":{"method":"DELETE","path":"/pets/{id}"},"authenticationRequirements":["apiKey"]}
                        ]}
                        """),
                "{\"result\":{\"tools\":[{\"name\":\"find_pets\"},{\"name\":\"delete_pet\"}]}}",
                policy(), objectMapper
        ));

        assertThat(report.path("overallRisk").stringValue()).isEqualTo("DESTRUCTIVE");
        assertThat(report.path("tools").path(0).path("name").stringValue()).isEqualTo("delete_pet");
        assertThat(report.path("tools").path(0).path("authenticationRequired").booleanValue()).isTrue();
        assertThat(report.path("sandbox").path("network").stringValue()).isEqualTo("none");
        assertThat(report.path("sandbox").path("readOnlyRootFilesystem").booleanValue()).isTrue();
        assertThat(report.path("protocol").path("toolInvocationPerformed").booleanValue()).isFalse();
        assertThat(report.path("findings")).isEmpty();
    }

    @Test
    void defaultsUndeclaredOrUnlistedToolsToDestructive() {
        JsonNode report = objectMapper.readTree(ToolSecurityReport.create(
                manifest("""
                        {"tools":[
                          {"name":"find_pets","risk":"UNKNOWN","sourceOperation":{"method":"GET","path":"/pets"},"authenticationRequirements":[]},
                          {"name":"update_pet","risk":"MUTATING","sourceOperation":{"method":"PATCH","path":"/pets/{id}"},"authenticationRequirements":[]}
                        ]}
                        """),
                "{\"result\":{\"tools\":[{\"name\":\"find_pets\"},{\"name\":\"surprise_tool\"}]}}",
                policy(), objectMapper
        ));

        assertThat(report.path("overallRisk").stringValue()).isEqualTo("DESTRUCTIVE");
        assertThat(report.path("findings").toString())
                .contains("no supported approved risk", "absent from Inspector", "undeclared tool");
        assertThat(report.path("tools").toString()).contains("CONSERVATIVE_DEFAULT");
    }

    @Test
    void upgradesAnUnderclassifiedDeleteOperationToDestructive() {
        JsonNode report = objectMapper.readTree(ToolSecurityReport.create(
                manifest("""
                        {"tools":[
                          {"name":"delete_pet","risk":"READ_ONLY","sourceOperation":{"method":"DELETE","path":"/pets/{id}"},"authenticationRequirements":[]}
                        ]}
                        """),
                "{\"result\":{\"tools\":[{\"name\":\"delete_pet\"}]}}",
                policy(), objectMapper
        ));

        assertThat(report.path("tools").path(0).path("risk").stringValue()).isEqualTo("DESTRUCTIVE");
        assertThat(report.path("tools").path(0).path("declaredRisk").stringValue()).isEqualTo("READ_ONLY");
        assertThat(report.path("tools").path(0).path("classificationBasis").stringValue())
                .isEqualTo("CONSERVATIVE_METHOD_UPGRADE");
        assertThat(report.path("findings").toString()).contains("upgraded from READ_ONLY to DESTRUCTIVE");
    }

    private String manifest(String contract) {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "files", java.util.List.of(java.util.Map.of("path", "contract.json", "content", contract))
        ));
    }

    private static ContainerSandboxPolicy policy() {
        return new ContainerSandboxPolicy(
                "65532:65532", "0.5", 256, 64, Duration.ofSeconds(30), "none"
        );
    }
}
