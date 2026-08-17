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
    private static final double TEXT_WEIGHT = 0.2;

    public RankedServer rank(McpServerEntity server, RequirementAnalysis requirement) {
        return rank(server, requirement, Set.of());
    }

    public RankedServer rank(
            McpServerEntity server,
            RequirementAnalysis requirement,
            Collection<String> serverCapabilities
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

        double textScore = Math.min(1.0, Math.max(0.0, points / maxPoints));
        CapabilityCoverage capabilityCoverage = capabilityCoverage(requirement, serverCapabilities);
        double score = textScore;
        if (capabilityCoverage.score() != null) {
            score = CAPABILITY_WEIGHT * capabilityCoverage.score() + TEXT_WEIGHT * textScore;
            reasons.add(0, "capability coverage %d/%d".formatted(
                    capabilityCoverage.matched().size(),
                    capabilityCoverage.matched().size() + capabilityCoverage.missing().size()
            ));
        }

        if ("deprecated".equalsIgnoreCase(server.getStatus())) {
            score *= 0.5;
            reasons.add("deprecated status penalty");
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
