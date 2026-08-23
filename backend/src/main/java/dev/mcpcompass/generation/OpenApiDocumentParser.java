package dev.mcpcompass.generation;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
class OpenApiDocumentParser {
    private static final Set<String> HTTP_METHODS = Set.of(
            "get", "put", "post", "delete", "options", "head", "patch", "trace"
    );

    private final ObjectMapper jsonMapper;
    private final ObjectMapper yamlMapper;

    OpenApiDocumentParser(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.yamlMapper = new YAMLMapper();
    }

    ParsedOpenApiDocument parse(byte[] content) {
        if (content.length == 0 || new String(content, StandardCharsets.UTF_8).isBlank()) {
            throw new OpenApiSourceException("EMPTY_OPENAPI_SOURCE", "The OpenAPI source is empty.");
        }

        JsonNode root = read(content);
        if (!root.isObject()) {
            throw new OpenApiSourceException("INVALID_OPENAPI_DOCUMENT", "The OpenAPI document must be an object.");
        }
        String version = text(root, "openapi");
        if (version == null) {
            throw new OpenApiSourceException(
                    "INVALID_OPENAPI_DOCUMENT",
                    "The document must declare a non-empty 'openapi' version."
            );
        }
        if (!version.matches("3\\.\\d+(?:\\.\\d+)?(?:[-+].*)?")) {
            throw new OpenApiSourceException(
                    "UNSUPPORTED_OPENAPI_VERSION",
                    "Only OpenAPI 3.x documents are supported."
            );
        }

        JsonNode paths = root.path("paths");
        int pathCount = paths.isObject() ? paths.size() : 0;
        int operationCount = 0;
        if (paths.isObject()) {
            for (JsonNode pathItem : paths) {
                if (!pathItem.isObject()) {
                    continue;
                }
                for (var field : pathItem.properties()) {
                    if (HTTP_METHODS.contains(field.getKey().toLowerCase(Locale.ROOT))) {
                        operationCount++;
                    }
                }
            }
        }

        JsonNode info = root.path("info");
        return new ParsedOpenApiDocument(
                version,
                text(info, "title"),
                text(info, "version"),
                pathCount,
                operationCount,
                root
        );
    }

    private JsonNode read(byte[] content) {
        try {
            return jsonMapper.readTree(content);
        } catch (JacksonException jsonFailure) {
            try {
                return yamlMapper.readTree(content);
            } catch (JacksonException yamlFailure) {
                throw new OpenApiSourceException(
                        "INVALID_OPENAPI_DOCUMENT",
                        "The source is not valid JSON or YAML.",
                        yamlFailure
                );
            }
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank() ? value.stringValue() : null;
    }

    record ParsedOpenApiDocument(
            String openApiVersion,
            String title,
            String apiVersion,
            int pathCount,
            int operationCount,
            JsonNode document
    ) {
    }
}
