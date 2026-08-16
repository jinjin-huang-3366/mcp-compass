package dev.mcpcompass.requirement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiRequirementAnalyzerTest {
    private final HeuristicRequirementAnalyzer heuristicAnalyzer = new HeuristicRequirementAnalyzer();
    private final OpenAiRequirementClient llmClient = mock(OpenAiRequirementClient.class);
    private final OpenAiRequirementAnalyzer analyzer =
            new OpenAiRequirementAnalyzer(heuristicAnalyzer, llmClient);

    @Test
    void combinesDeterministicKeywordsWithStructuredLlmAnalysis() {
        String requirement = "Read GitHub issues but never delete repositories";
        StructuredRequirement structuredRequirement = new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                "source-control",
                "github",
                List.of("github.issue.read"),
                List.of("github.repository.delete"),
                List.of()
        );
        when(llmClient.analyze(requirement)).thenReturn(structuredRequirement);

        RequirementAnalysis analysis = analyzer.analyze(requirement);

        assertThat(analysis.originalRequirement()).isEqualTo(requirement);
        assertThat(analysis.keywords()).contains("github", "issues", "never", "delete", "repositories");
        assertThat(analysis.structuredRequirement()).isEqualTo(structuredRequirement);
    }

    @Test
    void fallsBackToHeuristicAnalysisWhenLlmFails() {
        String requirement = "Read GitHub issues";
        when(llmClient.analyze(requirement)).thenThrow(
                new OpenAiRequirementClient.OpenAiRequirementClientException("provider unavailable")
        );

        RequirementAnalysis analysis = analyzer.analyze(requirement);

        assertThat(analysis.keywords()).containsExactly("read", "github", "issues");
        assertThat(analysis.structuredRequirement()).isEqualTo(StructuredRequirement.empty());
    }
}
