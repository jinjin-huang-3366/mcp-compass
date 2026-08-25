package dev.mcpcompass.validationworker;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

final class McpInspectorProtocolResult {
    static final String INSPECTOR_VERSION = "2.3.0";
    static final String METHOD = "tools/list";

    private McpInspectorProtocolResult() {
    }

    static String validateAndSerialize(String output, ObjectMapper objectMapper) throws Exception {
        JsonNode response = objectMapper.readTree(output);
        if (response == null || !response.isObject() || !response.path("result").path("tools").isArray()) {
            throw new IllegalArgumentException("MCP Inspector returned no tools/list result");
        }
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put("validator", "mcp-inspector");
        persisted.put("validatorVersion", INSPECTOR_VERSION);
        persisted.put("method", METHOD);
        persisted.put("response", response);
        return objectMapper.writeValueAsString(persisted);
    }
}
