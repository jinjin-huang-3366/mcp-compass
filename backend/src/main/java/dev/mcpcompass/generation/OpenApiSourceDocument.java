package dev.mcpcompass.generation;

import tools.jackson.databind.JsonNode;

public record OpenApiSourceDocument(
        SourceKind sourceKind,
        String sourceLocation,
        String openApiVersion,
        String title,
        String apiVersion,
        int pathCount,
        int operationCount,
        JsonNode document
) {
    public enum SourceKind { FILE, URL }
}
