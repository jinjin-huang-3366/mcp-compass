package dev.mcpcompass.search;

import java.util.List;
import java.util.UUID;

public record SearchResponse(
        String requirement,
        List<String> keywords,
        int page,
        int pageSize,
        int totalMatches,
        int totalPages,
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
            double qualityScore,
            Double capabilityCoverage,
            List<String> matchedCapabilities,
            List<String> missingCapabilities,
            RankingExplanation rankingExplanation,
            List<String> reasons
    ) {
    }

    public record RankingExplanation(
            List<RankingFeatureContribution> contributions,
            double preAdjustmentScore,
            double statusMultiplier
    ) {
    }

    public record RankingFeatureContribution(
            String feature,
            double featureScore,
            double weight,
            double contribution
    ) {
    }
}
