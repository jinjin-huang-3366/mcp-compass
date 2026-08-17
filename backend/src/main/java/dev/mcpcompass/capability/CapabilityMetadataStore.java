package dev.mcpcompass.capability;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CapabilityMetadataStore {
    void replaceForServer(UUID serverId, NormalizedCapabilityMetadata metadata);

    Map<UUID, Set<String>> findCapabilityNamesByServerIds(Collection<UUID> serverIds);
}
