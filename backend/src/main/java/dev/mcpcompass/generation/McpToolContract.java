package dev.mcpcompass.generation;

import tools.jackson.databind.JsonNode;

import java.util.List;

public record McpToolContract(
        String contractVersion,
        String status,
        Source source,
        List<Tool> tools
) {
    public McpToolContract {
        tools = List.copyOf(tools);
    }

    public record Source(
            String type,
            String location,
            String openApiVersion,
            String title,
            String apiVersion
    ) {
    }

    public record Tool(
            String name,
            String description,
            JsonNode inputSchema,
            JsonNode outputSchema,
            Operation sourceOperation,
            List<String> authenticationRequirements,
            Risk risk
    ) {
        public Tool {
            inputSchema = inputSchema.deepCopy();
            outputSchema = outputSchema.deepCopy();
            authenticationRequirements = List.copyOf(authenticationRequirements);
        }
    }

    public record Operation(String method, String path, String operationId) {
    }

    public enum Risk {
        READ_ONLY,
        MUTATING,
        DESTRUCTIVE
    }
}
