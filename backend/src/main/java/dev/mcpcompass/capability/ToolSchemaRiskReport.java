package dev.mcpcompass.capability;

import java.util.List;

public record ToolSchemaRiskReport(
        String revision,
        String inspectorVersion,
        boolean partial,
        List<InspectedSchema> schemas
) {
    public ToolSchemaRiskReport {
        schemas = List.copyOf(schemas);
    }

    public record InspectedSchema(
            String tool,
            String source,
            String sha256,
            Uncertainty uncertainty,
            List<Signal> signals
    ) {
        public InspectedSchema {
            signals = List.copyOf(signals);
        }
    }

    public enum Uncertainty {
        INFERRED,
        NO_SIGNAL,
        INCOMPLETE,
        UNAVAILABLE
    }

    public enum Signal {
        ARBITRARY_SQL,
        ARBITRARY_COMMAND,
        CALLER_CONTROLLED_APPROVAL
    }
}
