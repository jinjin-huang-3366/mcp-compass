package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RankingService {
    private static final double LEXICAL_WEIGHT = 0.85;
    private static final double ACTIVE_MAINTENANCE_WEIGHT = 0.05;
    private static final double OFFICIAL_PROVENANCE_WEIGHT = 0.03;
    private static final double PUBLIC_REPOSITORY_WEIGHT = 0.02;
    private static final double INSTALLABILITY_WEIGHT = 0.05;

    public RankedServer rank(McpServerEntity server, RequirementAnalysis requirement) {
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
            lexicalScore *= 0.5;
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

        double score = Math.min(1.0, Math.max(0.0, lexicalScore * LEXICAL_WEIGHT + featureScore));
        return new RankedServer(server, score, List.copyOf(reasons));
    }

    private static boolean isPublicRepository(String repositoryUrl) {
        return repositoryUrl != null
                && (repositoryUrl.startsWith("https://") || repositoryUrl.startsWith("http://"));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record RankedServer(McpServerEntity server, double score, List<String> reasons) {
    }
}
