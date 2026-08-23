package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;

import java.util.List;
import java.util.Objects;

public record ToolSchemaInspection(
        Status status,
        List<RegistryClient.RegistryToolPayload> tools
) {
    public ToolSchemaInspection {
        Objects.requireNonNull(status, "status must not be null");
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
    }

    public enum Status {
        DISCOVERED,
        PARTIAL,
        INVALID,
        NOT_DISCOVERABLE
    }
}
