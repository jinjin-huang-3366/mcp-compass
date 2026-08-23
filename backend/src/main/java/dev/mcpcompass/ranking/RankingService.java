package dev.mcpcompass.ranking;

import dev.mcpcompass.capability.CapabilityNameNormalizer;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class RankingService {
    private static final double CAPABILITY_WEIGHT = 0.8;
    private static final double SECONDARY_WEIGHT = 0.2;
    private static final double LEXICAL_WEIGHT = 0.85;
    private static final double QUALITY_WEIGHT = 0.15;

    public RankedServer rank(McpServerEntity server, RequirementAnalysis requirement) {
        return rank(server, requirement, Set.of(), null, TrustQualitySignals.unavailable());
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities
    ) {
        return rank(server, requirement, serverCapabilities, null, TrustQualitySignals.unavailable());
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities,
            Double vectorSimilarity
    ) {
        return rank(server, requirement, serverCapabilities, vectorSimilarity, TrustQualitySignals.unavailable());
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities,
            Double vectorSimilarity,
            TrustQualitySignals trustSignals
    ) {
        String name = lower(server.getRegistryName());
        String title = lower(server.getTitle());
        String description = lower(server.getDescription());

        double points = 0.0;
        double maxPoints = Math.max(1, requirement.keywords().size() * 3.0);
        List<String> reasons = new ArrayList<>();

        for (String keyword : requirement.keywords()) {
            if (title.contains(keyword)) {
                points += 3.0;
                reasons.add("title matches " + keyword);
            } else if (name.contains(keyword)) {
                points += 2.5;
                reasons.add("server name matches " + keyword);
            } else if (description.contains(keyword)) {
                points += 1.5;
                reasons.add("description matches " + keyword);
            }
        }

        double lexicalScore = Math.min(1.0, Math.max(0.0, points / maxPoints));
        if ("deprecated".equalsIgnoreCase(server.getStatus())) {
            reasons.add("deprecated status penalty");
        } else if ("active".equalsIgnoreCase(server.getStatus())) {
            reasons.add("active Registry status");
        }

        QualityAssessment quality = quality(server, trustSignals);
        reasons.addAll(quality.reasons());
        if (server.getPackageCount() > 0 || server.getRemoteCount() > 0) {
            if (server.getPackageCount() > 0) {
                reasons.add("installable package metadata available");
            }
            if (server.getRemoteCount() > 0) {
                reasons.add("remote endpoint metadata available");
            }
        }

        double retrievalScore = lexicalScore;
        if (vectorSimilarity != null) {
            double boundedSimilarity = Math.min(1.0, Math.max(0.0, vectorSimilarity));
            retrievalScore = Math.max(retrievalScore, boundedSimilarity);
            reasons.add("semantic similarity %d%%".formatted(Math.round(boundedSimilarity * 100.0)));
        }
        CapabilityCoverage capabilityCoverage = capabilityCoverage(requirement, serverCapabilities);
        List<RankingFeatureContribution> contributions = new ArrayList<>();
        double retrievalWeight = LEXICAL_WEIGHT;
        double qualityWeight = QUALITY_WEIGHT;
        if (capabilityCoverage.score() != null) {
            contributions.add(contribution("capabilityCoverage", capabilityCoverage.score(), CAPABILITY_WEIGHT));
            retrievalWeight *= SECONDARY_WEIGHT;
            qualityWeight *= SECONDARY_WEIGHT;
            reasons.add(0, "capability coverage %d/%d".formatted(
                    capabilityCoverage.matched().size(),
                    capabilityCoverage.matched().size() + capabilityCoverage.missing().size()
            ));
        }
        contributions.add(contribution("retrievalRelevance", retrievalScore, retrievalWeight));
        contributions.add(contribution("quality", quality.score(), qualityWeight));

        double preAdjustmentScore = contributions.stream()
                .mapToDouble(RankingFeatureContribution::contribution)
                .sum();
        double statusMultiplier = "deprecated".equalsIgnoreCase(server.getStatus()) ? 0.5 : 1.0;
        double score = preAdjustmentScore * statusMultiplier;

        RankingExplanation explanation = new RankingExplanation(
                List.copyOf(contributions),
                preAdjustmentScore,
                statusMultiplier
        );

        return new RankedServer(
                server,
                Math.min(1.0, Math.max(0.0, score)),
                quality.score(),
                capabilityCoverage.score(),
                capabilityCoverage.matched(),
                capabilityCoverage.missing(),
                explanation,
                List.copyOf(reasons)
        );
    }

    private static RankingFeatureContribution contribution(String feature, double featureScore, double weight) {
        return new RankingFeatureContribution(feature, featureScore, weight, featureScore * weight);
    }

    private static QualityAssessment quality(McpServerEntity server, TrustQualitySignals signals) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        if ("active".equalsIgnoreCase(server.getStatus())) {
            score += 0.20;
        }
        if (server.hasOfficialRegistryProvenance()) {
            score += 0.20;
            reasons.add("official Registry provenance");
        }
        if (isPublicRepository(server.getRepositoryUrl())) {
            score += 0.10;
            reasons.add("public source repository declared");
        }
        if (server.getPackageCount() > 0 || server.getRemoteCount() > 0) {
            score += 0.15;
        }
        if ("DISCOVERED".equalsIgnoreCase(server.getToolSchemaStatus())) {
            score += 0.15;
            reasons.add("declared tool schemas discovered");
        } else if ("PARTIAL".equalsIgnoreCase(server.getToolSchemaStatus())) {
            score += 0.075;
            reasons.add("some declared tool schemas discovered");
        }
        if (Boolean.FALSE.equals(signals.repositoryArchived())) {
            score += 0.10;
            reasons.add("GitHub repository is not archived");
        } else if (Boolean.TRUE.equals(signals.repositoryArchived())) {
            reasons.add("GitHub repository is archived");
        }
        if (signals.licenseSpdx() != null && !signals.licenseSpdx().isBlank()
                && !"NOASSERTION".equalsIgnoreCase(signals.licenseSpdx())) {
            score += 0.05;
            reasons.add("repository license declared");
        }
        if (signals.lastActivityAt() != null && signals.collectedAt() != null
                && !signals.lastActivityAt().isBefore(signals.collectedAt().minus(Duration.ofDays(365)))) {
            score += 0.05;
            reasons.add("repository activity within one year of enrichment");
        }
        return new QualityAssessment(Math.min(1.0, score), List.copyOf(reasons));
    }

    private static CapabilityCoverage capabilityCoverage(
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities
    ) {
        LinkedHashMap<String, String> requiredByKey = new LinkedHashMap<>();
        requirement.structuredRequirement().requiredCapabilities().forEach(capability -> {
            String key = CapabilityNameNormalizer.matchingKey(capability);
            if (key != null) {
                requiredByKey.putIfAbsent(key, CapabilityNameNormalizer.canonicalName(capability));
            }
        });
        List<String> requiredCapabilities = List.copyOf(requiredByKey.values());
        if (requiredCapabilities.isEmpty()) {
            return new CapabilityCoverage(null, List.of(), List.of());
        }

        Set<String> availableCapabilities = serverCapabilities.stream()
                .map(CapabilityNameNormalizer::matchingKey)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<String> matched = requiredByKey.entrySet().stream()
                .filter(entry -> availableCapabilities.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .toList();
        List<String> missing = requiredByKey.entrySet().stream()
                .filter(entry -> !availableCapabilities.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .toList();
        return new CapabilityCoverage(
                (double) matched.size() / requiredCapabilities.size(),
                matched,
                missing
        );
    }

    private static boolean isPublicRepository(String repositoryUrl) {
        return repositoryUrl != null
                && (repositoryUrl.startsWith("https://") || repositoryUrl.startsWith("http://"));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record CapabilityCoverage(Double score, List<String> matched, List<String> missing) {
    }

    private record QualityAssessment(double score, List<String> reasons) {
    }

    public record RankingFeatureContribution(
            String feature,
            double featureScore,
            double weight,
            double contribution
    ) {
    }

    public record RankingExplanation(
            List<RankingFeatureContribution> contributions,
            double preAdjustmentScore,
            double statusMultiplier
    ) {
    }

    public record RankedServer(
            McpServerEntity server,
            double score,
            double qualityScore,
            Double capabilityCoverage,
            List<String> matchedCapabilities,
            List<String> missingCapabilities,
            RankingExplanation rankingExplanation,
            List<String> reasons
    ) {
    }
}
