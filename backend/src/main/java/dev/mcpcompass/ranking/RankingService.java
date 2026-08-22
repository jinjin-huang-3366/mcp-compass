package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.springframework.stereotype.Component;

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
    private static final double ACTIVE_MAINTENANCE_WEIGHT = 0.05;
    private static final double OFFICIAL_PROVENANCE_WEIGHT = 0.03;
    private static final double PUBLIC_REPOSITORY_WEIGHT = 0.02;
    private static final double INSTALLABILITY_WEIGHT = 0.05;

    public RankedServer rank(McpServerEntity server, RequirementAnalysis requirement) {
        return rank(server, requirement, Set.of());
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities
    ) {
        return rank(server, requirement, serverCapabilities, null);
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities,
            Double vectorSimilarity
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

        double featureScore = 0.0;
        if ("active".equalsIgnoreCase(server.getStatus())) {
            featureScore += ACTIVE_MAINTENANCE_WEIGHT;
        }
        if (server.hasOfficialRegistryProvenance()) {
            featureScore += OFFICIAL_PROVENANCE_WEIGHT;
            reasons.add("official Registry provenance");
        }
        if (isPublicRepository(server.getRepositoryUrl())) {
            featureScore += PUBLIC_REPOSITORY_WEIGHT;
            reasons.add("public source repository declared");
        }
        if (server.getPackageCount() > 0 || server.getRemoteCount() > 0) {
            featureScore += INSTALLABILITY_WEIGHT;
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
                Math.max(0.0, retrievalScore * LEXICAL_WEIGHT + featureScore)
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
                capabilityCoverage.score(),
                capabilityCoverage.matched(),
                capabilityCoverage.missing(),
                List.copyOf(reasons)
        );
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

    public record RankedServer(
            McpServerEntity server,
            double score,
            Double capabilityCoverage,
            List<String> matchedCapabilities,
            List<String> missingCapabilities,
            List<String> reasons
    ) {
    }
}
