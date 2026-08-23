package dev.mcpcompass.generation;

import java.util.List;

public record McpToolContractReviewRequest(
        McpToolContract contract,
        List<ToolReview> tools
) {
    public record ToolReview(
            int toolIndex,
            boolean selected,
            String name,
            String description
    ) {
    }
}
