package dev.mcpcompass.search;

import java.util.UUID;

public record SearchDocument(UUID serverId, String registryName, int version, String content) {
}
