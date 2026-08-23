package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
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
        double secondaryScore = Math.min(
                1.0,
                Math.max(0.0, retrievalScore * LEXICAL_WEIGHT + quality.score() * QUALITY_WEIGHT)
        );
        CapabilityCoverage capabilityCoverage = capabilityCoverage(requirement, serverCapabilities);
        double score = secondaryScore;
        if (capabilityCoverage.score() != null) {
            score = CAPABILITY_WEIGHT * capabilityCoverage.score() + SECONDARY_WEIGHT * secondaryScore;
            reasons.add(0, "capability coverage %d/%d".formatted(
                    capabilityCoverage.matched().size(),
                    capabilityCoverage.matched().size() + capabilityCoverage.missing().size()
            ));
        }

        if ("deprecated".equalsIgnoreCase(server.getStatus())) {
            score *= 0.5;
        }

        return new RankedServer(
                server,
                Math.min(1.0, Math.max(0.0, score)),
                quality.score(),
                capabilityCoverage.score(),
                capabilityCoverage.matched(),
                capabilityCoverage.missing(),
                List.copyOf(reasons)
        );
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
        List<String> requiredCapabilities = requirement.structuredRequirement().requiredCapabilities().stream()
                .map(RankingService::canonicalCapability)
                .toList();
        if (requiredCapabilities.isEmpty()) {
            return new CapabilityCoverage(null, List.of(), List.of());
        }

        Set<String> availableCapabilities = serverCapabilities.stream()
                .map(RankingService::canonicalCapability)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> matched = requiredCapabilities.stream()
                .filter(availableCapabilities::contains)
                .toList();
        List<String> missing = requiredCapabilities.stream()
                .filter(capability -> !availableCapabilities.contains(capability))
                .toList();
        return new CapabilityCoverage(
                (double) matched.size() / requiredCapabilities.size(),
                matched,
                missing
        );
    }

    private static String canonicalCapability(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
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

    public record RankedServer(
            McpServerEntity server,
            double score,
            double qualityScore,
            Double capabilityCoverage,
            List<String> matchedCapabilities,
            List<String> missingCapabilities,
            List<String> reasons
    ) {
    }
}
