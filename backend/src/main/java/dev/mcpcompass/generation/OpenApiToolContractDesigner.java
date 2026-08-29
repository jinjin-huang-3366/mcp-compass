package dev.mcpcompass.generation;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
class OpenApiToolContractDesigner {
    private static final String CONTRACT_VERSION = "1.0";
    private static final int MAX_SCHEMA_RESOLUTION_DEPTH = 64;
    private static final int MAX_RESOLVED_SCHEMA_NODES = 10_000;
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace"
    );

    private final ObjectMapper objectMapper;

    OpenApiToolContractDesigner(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    McpToolContract design(OpenApiSourceDocument source) {
        List<McpToolContract.Tool> tools = new ArrayList<>();
        Map<String, Integer> usedNames = new HashMap<>();
        JsonNode root = source.document();
        JsonNode paths = root.path("paths");
        if (paths.isObject()) {
            for (var pathEntry : paths.properties()) {
                if (!pathEntry.getValue().isObject()) {
                    continue;
                }
                JsonNode pathParameters = pathEntry.getValue().path("parameters");
                for (var operationEntry : pathEntry.getValue().properties()) {
                    String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                    JsonNode operation = operationEntry.getValue();
                    if (!HTTP_METHODS.contains(method) || !operation.isObject()) {
                        continue;
                    }
                    tools.add(tool(root, pathEntry.getKey(), method, operation, pathParameters, usedNames));
                }
            }
        }
        return new McpToolContract(
                CONTRACT_VERSION,
                "PROPOSED",
                new McpToolContract.Source(
                        source.sourceKind().name(), source.sourceLocation(), source.openApiVersion(),
                        source.title(), source.apiVersion()
                ),
                tools
        );
    }

    private McpToolContract.Tool tool(
            JsonNode root,
            String path,
            String method,
            JsonNode operation,
            JsonNode pathParameters,
            Map<String, Integer> usedNames
    ) {
        String operationId = text(operation, "operationId");
        String baseName = toolName(operationId == null ? method + "_" + path : operationId);
        int occurrence = usedNames.merge(baseName, 1, Integer::sum);
        String name = occurrence == 1 ? baseName : baseName + "_" + occurrence;
        String description = firstText(operation, "summary", "description");
        if (description == null) {
            description = method.toUpperCase(Locale.ROOT) + " " + path;
        }
        return new McpToolContract.Tool(
                name,
                description,
                inputSchema(root, pathParameters, operation.path("parameters"), operation.path("requestBody")),
                outputSchema(root, operation.path("responses")),
                new McpToolContract.Operation(method.toUpperCase(Locale.ROOT), path, operationId),
                authentication(root, operation),
                risk(method)
        );
    }

    private ObjectNode inputSchema(
            JsonNode root,
            JsonNode pathParameters,
            JsonNode operationParameters,
            JsonNode requestBody
    ) {
        ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        LinkedHashSet<String> required = new LinkedHashSet<>();
        addParameters(root, pathParameters, properties, required);
        addParameters(root, operationParameters, properties, required);
        if (requestBody.isObject()) {
            JsonNode bodySchema = contentSchema(requestBody.path("content"));
            if (bodySchema != null) {
                properties.set("body", resolveSchema(root, bodySchema));
                if (requestBody.path("required").asBoolean(false)) {
                    required.add("body");
                }
            }
        }
        if (!required.isEmpty()) {
            ArrayNode requiredNode = schema.putArray("required");
            required.forEach(requiredNode::add);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private void addParameters(
            JsonNode root,
            JsonNode parameters,
            ObjectNode properties,
            Set<String> required
    ) {
        if (!parameters.isArray()) {
            return;
        }
        for (JsonNode parameter : parameters) {
            String name = text(parameter, "name");
            JsonNode parameterSchema = parameter.path("schema");
            if (name == null || !parameterSchema.isObject()) {
                continue;
            }
            properties.set(name, resolveSchema(root, parameterSchema));
            if (parameter.path("required").asBoolean(false) || "path".equals(text(parameter, "in"))) {
                required.add(name);
            }
        }
    }

    private JsonNode outputSchema(JsonNode root, JsonNode responses) {
        if (responses.isObject()) {
            for (var response : responses.properties()) {
                if (response.getKey().matches("2\\d\\d")) {
                    JsonNode schema = contentSchema(response.getValue().path("content"));
                    if (schema != null) {
                        return resolveSchema(root, schema);
                    }
                }
            }
            JsonNode schema = contentSchema(responses.path("default").path("content"));
            if (schema != null) {
                return resolveSchema(root, schema);
            }
        }
        return objectMapper.createObjectNode().put("type", "object");
    }

    private JsonNode resolveSchema(JsonNode root, JsonNode schema) {
        return resolveSchema(root, schema, new LinkedHashSet<>(), new int[]{0}, 0);
    }

    private JsonNode resolveSchema(
            JsonNode root,
            JsonNode schema,
            Set<String> activeReferences,
            int[] resolvedNodes,
            int depth
    ) {
        if (depth > MAX_SCHEMA_RESOLUTION_DEPTH || ++resolvedNodes[0] > MAX_RESOLVED_SCHEMA_NODES) {
            throw invalidSchemaReference("OpenAPI schema references are too deeply nested or expand too far.");
        }
        if (schema.isArray()) {
            ArrayNode resolved = objectMapper.createArrayNode();
            for (JsonNode item : schema) {
                resolved.add(resolveSchema(root, item, activeReferences, resolvedNodes, depth + 1));
            }
            return resolved;
        }
        if (!schema.isObject()) {
            return schema.deepCopy();
        }

        JsonNode referenceNode = schema.path("$ref");
        if (!referenceNode.isMissingNode()) {
            if (!referenceNode.isString() || referenceNode.stringValue().isBlank()) {
                throw invalidSchemaReference("OpenAPI schema references must be non-empty strings.");
            }
            String reference = referenceNode.stringValue();
            if (!reference.startsWith("#/")) {
                throw invalidSchemaReference("Only local OpenAPI schema references are supported: " + reference);
            }
            if (!activeReferences.add(reference)) {
                throw invalidSchemaReference("Circular OpenAPI schema references are not supported: " + reference);
            }
            try {
                JsonNode target = root.at(reference.substring(1));
                if (target.isMissingNode() || !target.isObject()) {
                    throw invalidSchemaReference("OpenAPI schema reference cannot be resolved: " + reference);
                }
                JsonNode resolvedTarget = resolveSchema(
                        root, target, activeReferences, resolvedNodes, depth + 1
                );
                if (!(resolvedTarget instanceof ObjectNode resolvedObject)) {
                    throw invalidSchemaReference("OpenAPI schema reference must resolve to an object: " + reference);
                }
                ObjectNode merged = resolvedObject.deepCopy();
                for (var property : schema.properties()) {
                    if (!"$ref".equals(property.getKey())) {
                        merged.set(property.getKey(), resolveSchema(
                                root, property.getValue(), activeReferences, resolvedNodes, depth + 1
                        ));
                    }
                }
                return merged;
            } finally {
                activeReferences.remove(reference);
            }
        }

        ObjectNode resolved = objectMapper.createObjectNode();
        for (var property : schema.properties()) {
            resolved.set(property.getKey(), resolveSchema(
                    root, property.getValue(), activeReferences, resolvedNodes, depth + 1
            ));
        }
        return resolved;
    }

    private static OpenApiSourceException invalidSchemaReference(String message) {
        return new OpenApiSourceException("INVALID_OPENAPI_DOCUMENT", message);
    }

    private JsonNode contentSchema(JsonNode content) {
        if (!content.isObject()) {
            return null;
        }
        JsonNode jsonSchema = content.path("application/json").path("schema");
        if (!jsonSchema.isMissingNode()) {
            return jsonSchema;
        }
        for (JsonNode mediaType : content) {
            JsonNode schema = mediaType.path("schema");
            if (!schema.isMissingNode()) {
                return schema;
            }
        }
        return null;
    }

    private List<String> authentication(JsonNode root, JsonNode operation) {
        JsonNode security = operation.has("security") ? operation.path("security") : root.path("security");
        if (!security.isArray() || security.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> requirements = new LinkedHashSet<>();
        for (JsonNode alternative : security) {
            if (alternative.isObject()) {
                alternative.propertyStream().map(Map.Entry::getKey).forEach(requirements::add);
            }
        }
        return List.copyOf(requirements);
    }

    private static McpToolContract.Risk risk(String method) {
        return switch (method) {
            case "get", "head", "options" -> McpToolContract.Risk.READ_ONLY;
            case "delete" -> McpToolContract.Risk.DESTRUCTIVE;
            default -> McpToolContract.Risk.MUTATING;
        };
    }

    private static String toolName(String value) {
        String normalized = value.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? "call_api" : normalized;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue().trim() : null;
    }
}
