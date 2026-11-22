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
            JsonNode remotes = declaration(item).path("remotes");
            int selected = selectedRemoteIndex(remotes);
            if (selected >= 0) {
                return Optional.of(URI.create(remotes.path(selected).path("url").stringValue()));
            }
        } catch (RuntimeException ignored) {
            // Malformed publisher metadata is uncheckable, not an unavailable server.
        }
        return Optional.empty();
    }

    static JsonNode declaration(JsonNode item) {
        return item.has("server") ? item.path("server") : item;
    }

    static int selectedRemoteIndex(JsonNode remotes) {
        if (remotes.isArray()) {
            for (int i = 0; i < remotes.size(); i++) {
                JsonNode remote = remotes.path(i);
                if ("streamable-http".equalsIgnoreCase(remote.path("type").asText()) && remote.path("url").isString()) {
                    return i;
                }
            }
        }
        return -1;
    }
}
