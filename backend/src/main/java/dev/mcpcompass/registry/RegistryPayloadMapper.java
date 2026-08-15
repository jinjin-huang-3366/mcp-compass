package dev.mcpcompass.registry;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RegistryPayloadMapper {
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
                String status = text(item, "status").or(() -> text(server, "status")).orElse("active");
                servers.add(new RegistryClient.RegistryServerPayload(
                        text(server, "name").orElse(null),
                        text(server, "title").orElse(null),
                        text(server, "description").orElse(null),
                        text(server, "version").orElse(null),
                        status,
                        objectMapper.writeValueAsString(item)
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
}
