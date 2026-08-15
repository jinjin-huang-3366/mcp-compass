package dev.mcpcompass.requirement;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HeuristicRequirementAnalyzer implements RequirementAnalyzer {
    private static final Set<String> STOP_WORDS = Set.of(
            "i", "a", "an", "the", "my", "our", "agent", "need", "needs", "to", "and", "or", "but",
            "can", "should", "with", "for", "from", "of", "on", "in", "into", "that", "this", "it", "be"
    );

    @Override
    public RequirementAnalysis analyze(String requirement) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        Arrays.stream(requirement.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(token -> token.length() > 2)
                .filter(token -> !STOP_WORDS.contains(token))
                .limit(12)
                .forEach(keywords::add);
        return new RequirementAnalysis(requirement, List.copyOf(keywords));
    }
}
