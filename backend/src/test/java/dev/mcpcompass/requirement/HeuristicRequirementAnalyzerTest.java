package dev.mcpcompass.requirement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicRequirementAnalyzerTest {
    private final HeuristicRequirementAnalyzer analyzer = new HeuristicRequirementAnalyzer();

    @Test
    void extractsUsefulKeywordsAndDropsStopWords() {
        RequirementAnalysis result = analyzer.analyze("I need my agent to read GitHub issues, comment on them and create pull requests");

        assertThat(result.keywords())
                .contains("github", "issues", "comment", "create", "pull", "requests")
                .doesNotContain("agent", "need", "and");
    }
}
