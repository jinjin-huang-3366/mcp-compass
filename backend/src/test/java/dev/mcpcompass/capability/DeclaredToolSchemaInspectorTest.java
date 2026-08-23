package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

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
