package dev.mcpcompass.search;

import dev.mcpcompass.ranking.RankingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class StrongMatchPolicy {
    public static final double CONFIDENCE_THRESHOLD = 0.30;

    public Assessment assess(
            List<RankingService.RankedServer> rankedCandidates,
            int retrievedCandidates,
            int excludedCandidates
    ) {
        List<RankingService.RankedServer> strongMatches = rankedCandidates.stream()
                .filter(candidate -> candidate.score() >= CONFIDENCE_THRESHOLD)
                .toList();
        if (!strongMatches.isEmpty()) {
            return new Assessment(true, strongMatches, List.of());
        }

        List<String> reasons = new ArrayList<>();
        if (retrievedCandidates == 0) {
            reasons.add("No local candidates matched the parsed intent.");
        } else if (rankedCandidates.isEmpty() && excludedCandidates > 0) {
            reasons.add("All retrieved candidates were excluded by the parsed hard constraints.");
        } else if (!rankedCandidates.isEmpty()) {
            RankingService.RankedServer best = rankedCandidates.getFirst();
            reasons.add("Best candidate confidence %d%% is below the calibrated strong-match threshold of %d%%."
                    .formatted(percent(best.score()), percent(CONFIDENCE_THRESHOLD)));
            if (!best.missingCapabilities().isEmpty()) {
                reasons.add("Best candidate is missing required capabilities: "
                        + String.join(", ", best.missingCapabilities()) + ".");
            }
        } else {
            reasons.add("No eligible candidate had positive relevance evidence.");
        }
        return new Assessment(false, List.of(), List.copyOf(reasons));
    }

    private static long percent(double value) {
        return Math.round(value * 100.0);
    }

    public record Assessment(
            boolean strongMatch,
            List<RankingService.RankedServer> matches,
            List<String> abstentionReasons
    ) {
    }
}
