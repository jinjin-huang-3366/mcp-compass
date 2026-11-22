package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Inspects schemas already present in Registry metadata. This deliberately performs no package,
 * command, or MCP tool execution.
 */
@Component
public class DeclaredToolSchemaInspector {
    static final int MAX_SCHEMA_BYTES = 256 * 1024;
    static final int MAX_SCHEMA_DEPTH = 64;

    private final ObjectMapper objectMapper;

    public DeclaredToolSchemaInspector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ToolSchemaInspection inspect(RegistryClient.RegistryServerPayload payload) {
        if (payload.tools().isEmpty()) {
            return new ToolSchemaInspection(ToolSchemaInspection.Status.NOT_DISCOVERABLE, List.of(), null);
        }

        List<RegistryClient.RegistryToolPayload> inspected = new ArrayList<>();
        List<ToolSchemaRiskReport.InspectedSchema> schemas = new ArrayList<>();
        int declaredSchemas = 0;
        int validSchemas = 0;
        for (RegistryClient.RegistryToolPayload tool : payload.tools()) {
            if (tool.inputSchema() != null && !tool.inputSchema().isBlank()) {
                declaredSchemas++;
            }
            JsonNode schema = validSchema(tool.inputSchema());
            if (schema != null) {
                validSchemas++;
            }
            String schemaText = schema == null ? null : objectMapper.writeValueAsString(schema);
            if (schemas.size() < ToolSchemaRiskInspector.MAX_TOOLS) {
                schemas.add(ToolSchemaRiskInspector.inspect(tool.name(), tool.schemaSource(), schemaText, schema));
            }
            inspected.add(new RegistryClient.RegistryToolPayload(
                    tool.name(), tool.description(), schemaText, tool.capabilities(), tool.schemaSource()
            ));
        }

        ToolSchemaInspection.Status status;
        if (declaredSchemas == 0) {
            status = ToolSchemaInspection.Status.NOT_DISCOVERABLE;
        } else if (validSchemas == inspected.size()) {
            status = ToolSchemaInspection.Status.DISCOVERED;
        } else if (validSchemas > 0) {
            status = ToolSchemaInspection.Status.PARTIAL;
        } else {
            status = ToolSchemaInspection.Status.INVALID;
        }
        boolean partial = schemas.size() < inspected.size() || schemas.stream().anyMatch(schema ->
                schema.uncertainty() == ToolSchemaRiskReport.Uncertainty.INCOMPLETE
                        || schema.uncertainty() == ToolSchemaRiskReport.Uncertainty.UNAVAILABLE);
        return new ToolSchemaInspection(status, inspected, new ToolSchemaRiskReport(
                ToolSchemaRiskInspector.label(payload.version(), null), ToolSchemaRiskInspector.VERSION, partial, schemas
        ));
    }

    private JsonNode validSchema(String value) {
        if (value == null || value.length() > MAX_SCHEMA_BYTES || value.isBlank()
                || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SCHEMA_BYTES) {
            return null;
        }
        try {
            JsonNode schema = objectMapper.readTree(value);
            if (!schema.isObject() || depth(schema, 1) > MAX_SCHEMA_DEPTH) {
                return null;
            }
            return schema;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int depth(JsonNode node, int current) {
        if (current > MAX_SCHEMA_DEPTH) {
            return current;
        }
        int maximum = current;
        for (JsonNode child : node) {
            maximum = Math.max(maximum, depth(child, current + 1));
            if (maximum > MAX_SCHEMA_DEPTH) {
                break;
            }
        }
        return maximum;
    }
}
