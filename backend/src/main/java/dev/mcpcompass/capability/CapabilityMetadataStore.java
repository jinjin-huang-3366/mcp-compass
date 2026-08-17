package dev.mcpcompass.capability;

import java.util.UUID;

public interface CapabilityMetadataStore {
    void replaceForServer(UUID serverId, NormalizedCapabilityMetadata metadata);
}
