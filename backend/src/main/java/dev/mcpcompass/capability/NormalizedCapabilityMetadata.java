package dev.mcpcompass.capability;

import java.util.List;
import java.util.Objects;

public record NormalizedCapabilityMetadata(
        List<NormalizedTool> tools,
        List<NormalizedCapability> serverCapabilities
) {
    public NormalizedCapabilityMetadata {
        tools = List.copyOf(Objects.requireNonNull(tools, "tools must not be null"));
        serverCapabilities = List.copyOf(Objects.requireNonNull(
                serverCapabilities,
                "serverCapabilities must not be null"
        ));
    }

    public record NormalizedTool(
            String name,
            String description,
            String inputSchema,
            List<NormalizedCapability> capabilities
    ) {
        public NormalizedTool {
            Objects.requireNonNull(name, "name must not be null");
            capabilities = List.copyOf(Objects.requireNonNull(
                    capabilities,
                    "capabilities must not be null"
            ));
        }
    }

    public record NormalizedCapability(
            String canonicalName,
            String description,
            double confidence,
            String source
    ) {
        public NormalizedCapability {
            Objects.requireNonNull(canonicalName, "canonicalName must not be null");
            Objects.requireNonNull(source, "source must not be null");
            if (confidence < 0.0 || confidence > 1.0) {
                throw new IllegalArgumentException("confidence must be between zero and one");
            }
        }
    }
}
