package dev.mcpcompass.search;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String requirement,
        List<String> keywords,
        List<Match> matches
) {
    public record Match(
            UUID id,
            String registryName,
            String title,
            String description,
            String version,
            String status,
            double score,
            List<String> reasons
    ) {
    }
}
