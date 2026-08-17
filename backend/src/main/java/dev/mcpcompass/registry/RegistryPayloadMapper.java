package dev.mcpcompass.registry;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RegistryPayloadMapper {
    private static final String OFFICIAL_METADATA = "io.modelcontextprotocol.registry/official";
    private static final String PUBLISHER_METADATA = "io.modelcontextprotocol.registry/publisher-provided";

    private final ObjectMapper objectMapper;

    public RegistryPayloadMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RegistryClient.RegistryPage map(String body) {
        if (body == null || body.isBlank()) {
            return new RegistryClient.RegistryPage(List.of(), null);
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            List<RegistryClient.RegistryServerPayload> servers = new ArrayList<>();
            for (JsonNode item : root.path("servers")) {
                JsonNode server = item.has("server") ? item.path("server") : item;
                JsonNode officialMetadata = item.path("_meta").path(OFFICIAL_METADATA);
                String status = text(item, "status")
                        .or(() -> text(officialMetadata, "status"))
                        .or(() -> text(server, "status"))
                        .orElse("active");
                servers.add(new RegistryClient.RegistryServerPayload(
                        text(server, "name").orElse(null),
                        text(server, "title").orElse(null),
                        text(server, "description").orElse(null),
                        text(server, "version").orElse(null),
                        status,
                        objectMapper.writeValueAsString(item),
                        tools(server),
                        serverCapabilities(server)
                ));
            }
            String nextCursor = text(root.path("metadata"), "nextCursor").orElse(null);
            return new RegistryClient.RegistryPage(servers, nextCursor);
        } catch (Exception e) {
            throw new RegistryClient.RegistryClientException("Unable to parse MCP Registry response", e);
        }
    }

    private static Optional<String> text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.stringValue().isBlank()
                ? Optional.of(value.stringValue())
                : Optional.empty();
    }

    private List<RegistryClient.RegistryToolPayload> tools(JsonNode server) throws Exception {
        List<RegistryClient.RegistryToolPayload> tools = new ArrayList<>();
        appendTools(server.path("tools"), tools);
        appendTools(server.path("_meta").path(PUBLISHER_METADATA).path("tools"), tools);
        return List.copyOf(tools);
    }

    private void appendTools(
            JsonNode values,
            List<RegistryClient.RegistryToolPayload> tools
    ) throws Exception {
        if (!values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            String name = text(value, "name").orElse(null);
            JsonNode inputSchema = value.has("inputSchema")
                    ? value.path("inputSchema")
                    : value.path("input_schema");
            tools.add(new RegistryClient.RegistryToolPayload(
                    name,
                    text(value, "description").orElse(null),
                    inputSchema.isMissingNode() || inputSchema.isNull()
                            ? null
                            : objectMapper.writeValueAsString(inputSchema),
                    capabilityValues(value.path("capabilities"))
            ));
        }
    }

    private List<RegistryClient.RegistryCapabilityPayload> serverCapabilities(JsonNode server) {
        List<RegistryClient.RegistryCapabilityPayload> capabilities = new ArrayList<>();
        appendCapabilities(server.path("capabilities"), capabilities);
        appendCapabilities(
                server.path("_meta").path(PUBLISHER_METADATA).path("capabilities"),
                capabilities
        );
        return List.copyOf(capabilities);
    }

    private List<RegistryClient.RegistryCapabilityPayload> capabilityValues(JsonNode values) {
        List<RegistryClient.RegistryCapabilityPayload> capabilities = new ArrayList<>();
        appendCapabilities(values, capabilities);
        return List.copyOf(capabilities);
    }

    private static void appendCapabilities(
            JsonNode values,
            List<RegistryClient.RegistryCapabilityPayload> capabilities
    ) {
        if (!values.isArray()) {
            return;
        }
        for (JsonNode value : values) {
            if (value.isString()) {
                capabilities.add(new RegistryClient.RegistryCapabilityPayload(
                        value.stringValue(),
                        null
                ));
                continue;
            }
            if (value.isObject()) {
                capabilities.add(new RegistryClient.RegistryCapabilityPayload(
                        text(value, "name")
                                .or(() -> text(value, "canonicalName"))
                                .orElse(null),
                        text(value, "description").orElse(null)
                ));
            }
        }
    }
}
