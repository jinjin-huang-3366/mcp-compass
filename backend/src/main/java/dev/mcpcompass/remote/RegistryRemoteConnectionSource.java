package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Optional;

@Component
public class RegistryRemoteConnectionSource implements RemoteConnectionSource {
    private final ObjectMapper mapper;

    public RegistryRemoteConnectionSource(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<URI> endpoint(McpServerEntity server) {
        try {
            JsonNode item = mapper.readTree(server.getRawMetadata());
            JsonNode declaration = item.has("server") ? item.path("server") : item;
            for (JsonNode remote : declaration.path("remotes")) {
                if (!remote.path("url").isString() || !remote.path("type").isString()) {
                    continue;
                }
                String type = remote.path("type").stringValue();
                if ("streamable-http".equalsIgnoreCase(type)) {
                    return Optional.of(URI.create(remote.path("url").stringValue()));
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed publisher metadata is uncheckable, not an unavailable server.
        }
        return Optional.empty();
    }
}
