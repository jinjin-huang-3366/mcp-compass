package dev.mcpcompass.requirement;

import java.util.List;
import java.util.Objects;

public record RequirementAnalysis(
        String originalRequirement,
        List<String> keywords,
        StructuredRequirement structuredRequirement
) {
    public RequirementAnalysis {
        originalRequirement = Objects.requireNonNull(originalRequirement, "originalRequirement must not be null");
        keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords must not be null"));
        structuredRequirement = Objects.requireNonNull(structuredRequirement, "structuredRequirement must not be null");
    }

    public RequirementAnalysis(String originalRequirement, List<String> keywords) {
        this(originalRequirement, keywords, StructuredRequirement.empty());
    }
}
