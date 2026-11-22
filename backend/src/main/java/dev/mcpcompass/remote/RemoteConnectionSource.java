package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import java.net.URI;
import java.util.Optional;

/** Selects one declared remote endpoint; it never discovers endpoints by executing packages. */
public interface RemoteConnectionSource {
    Optional<URI> endpoint(McpServerEntity server);
}
