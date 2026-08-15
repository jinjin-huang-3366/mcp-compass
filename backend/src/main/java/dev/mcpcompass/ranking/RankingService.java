package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class RankingService {

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

        if ("deprecated".equalsIgnoreCase(server.getStatus())) {
            points *= 0.5;
            reasons.add("deprecated status penalty");
        }

        double score = Math.min(1.0, Math.max(0.0, points / maxPoints));
        return new RankedServer(server, score, List.copyOf(reasons));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    public record RankedServer(McpServerEntity server, double score, List<String> reasons) {
    }
}
