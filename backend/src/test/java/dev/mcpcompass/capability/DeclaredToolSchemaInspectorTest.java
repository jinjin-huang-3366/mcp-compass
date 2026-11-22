package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredToolSchemaInspectorTest {
    private final DeclaredToolSchemaInspector inspector = new DeclaredToolSchemaInspector(new ObjectMapper());

    @Test
    void acceptsBoundedObjectSchemasWithoutExecutingTheServer() {
        ToolSchemaInspection result = inspector.inspect(server(List.of(tool(
                "search", "{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}}}"
        ))));

        assertThat(result.status()).isEqualTo(ToolSchemaInspection.Status.DISCOVERED);
        assertThat(result.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.inputSchema()).contains("\"properties\"");
            assertThat(tool.schemaSource()).isEqualTo("registry-server-metadata");
        });
    }

    @Test
    void rejectsNonObjectAndOversizedSchemasButKeepsSafeToolMetadata() {
        String oversized = "{\"description\":\"" + "x".repeat(DeclaredToolSchemaInspector.MAX_SCHEMA_BYTES) + "\"}";
        ToolSchemaInspection result = inspector.inspect(server(List.of(
                tool("array-schema", "[]"), tool("oversized-schema", oversized)
        )));

        assertThat(result.status()).isEqualTo(ToolSchemaInspection.Status.INVALID);
        assertThat(result.tools()).extracting(RegistryClient.RegistryToolPayload::name)
                .containsExactly("array-schema", "oversized-schema");
        assertThat(result.tools()).extracting(RegistryClient.RegistryToolPayload::inputSchema)
                .containsOnlyNulls();
    }

    @Test
    void reportsSchemasThatAreNotDiscoverableFromStaticMetadata() {
        ToolSchemaInspection result = inspector.inspect(server(List.of(tool("listed-without-schema", null))));

        assertThat(result.status()).isEqualTo(ToolSchemaInspection.Status.NOT_DISCOVERABLE);
        assertThat(result.tools()).singleElement();
        assertThat(result.riskReport().partial()).isTrue();
        assertThat(result.riskReport().schemas()).singleElement().satisfies(schema -> {
            assertThat(schema.sha256()).isNull();
            assertThat(schema.uncertainty()).isEqualTo(ToolSchemaRiskReport.Uncertainty.UNAVAILABLE);
        });
    }

    @Test
    void detectsNestedCallerControlledFieldsWithProvenance() {
        var payload = server(List.of(tool("preview", """
                {"properties":{"options":{"type":"object","properties":{
                  "statement":{"type":["string","null"],"description":"SQL input","maxLength":200},
                  "shell_command":{"oneOf":[{"type":"string"},{"type":"null"}]},
                  "minApprovals":{"type":"integer","minimum":0,"maximum":4}
                }}}}
                """)));
        var report = inspector.inspect(payload).riskReport();
        assertThat(report.revision()).isEqualTo("1.0.0");
        assertThat(report.inspectorVersion()).isEqualTo("1");
        assertThat(report.partial()).isFalse();
        assertThat(report.schemas()).singleElement().satisfies(schema -> {
            assertThat(schema.signals()).containsExactly(
                    ToolSchemaRiskReport.Signal.ARBITRARY_SQL, ToolSchemaRiskReport.Signal.ARBITRARY_COMMAND,
                    ToolSchemaRiskReport.Signal.CALLER_CONTROLLED_APPROVAL);
            assertThat(schema.uncertainty()).isEqualTo(ToolSchemaRiskReport.Uncertainty.INFERRED);
            assertThat(schema.source()).isEqualTo("registry-server-metadata");
            assertThat(schema.sha256()).matches("[0-9a-f]{64}");
            assertThat(schema.sha256()).isEqualTo(inspector.inspect(payload).riskReport().schemas().getFirst().sha256());
        });
    }

    @Test
    void distinguishesTextSearchAndFixedChoicesFromCallerControlledOperations() {
        var result = inspector.inspect(server(List.of(tool("search_docs", """
                {"properties":{
                  "query":{"type":"string","description":"Find matching report titles"},
                  "command":{"type":"string","enum":["preview","list"]},
                  "minApprovals":{"type":"integer","minimum":3,"maximum":3},
                  "requireApproval":{"type":"boolean","const":true}
                },"examples":[{"sql":"Example used in documentation"}]}
                """))));
        assertThat(result.riskReport().schemas()).singleElement().satisfies(schema -> {
            assertThat(schema.signals()).isEmpty();
            assertThat(schema.uncertainty()).isEqualTo(ToolSchemaRiskReport.Uncertainty.NO_SIGNAL);
        });
    }

    @Test
    void recordsPartialInspectionForUnresolvedRefsAndBounds() {
        var limited = IntStream.range(0, ToolSchemaRiskInspector.MAX_TOOLS + 1)
                .mapToObj(index -> tool("tool_" + index, """
                        {"properties":{"options":{"$ref":"#/$defs/options"}}}
                        """))
                .toList();
        var result = inspector.inspect(server(limited));
        assertThat(result.tools()).hasSize(limited.size());
        assertThat(result.riskReport().partial()).isTrue();
        assertThat(result.riskReport().schemas()).hasSize(ToolSchemaRiskInspector.MAX_TOOLS)
                .allSatisfy(schema -> assertThat(schema.uncertainty())
                        .isEqualTo(ToolSchemaRiskReport.Uncertainty.INCOMPLETE));

        String wideSchema = "{\"allOf\":[" + "{}, ".repeat(ToolSchemaRiskInspector.MAX_NODES) + "{}]}";
        var wide = inspector.inspect(server(List.of(tool("wide", wideSchema))));
        assertThat(wide.riskReport().partial()).isTrue();
        assertThat(wide.riskReport().schemas().getFirst().uncertainty())
                .isEqualTo(ToolSchemaRiskReport.Uncertainty.INCOMPLETE);
    }

    private static RegistryClient.RegistryToolPayload tool(String name, String schema) {
        return new RegistryClient.RegistryToolPayload(
                name, "Description", schema, List.of(), "registry-server-metadata");
    }

    private static RegistryClient.RegistryServerPayload server(List<RegistryClient.RegistryToolPayload> tools) {
        return new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}", tools, List.of());
    }
}
