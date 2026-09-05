package dev.mcpcompass.requirement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicRequirementAnalyzerTest {
    private final HeuristicRequirementAnalyzer analyzer = new HeuristicRequirementAnalyzer();

    @Test
    void extractsUsefulKeywordsAndDropsStopWords() {
        RequirementAnalysis result = analyzer.analyze("I need my agent to read GitHub issues, comment on them and create pull requests");

        assertThat(result.keywords())
                .contains("github", "issues", "comment", "create", "pull", "requests")
                .doesNotContain("agent", "need", "and");
        assertThat(result.structuredRequirement()).isEqualTo(StructuredRequirement.empty());
    }

    @Test
    void parsesForbiddenCapabilitiesWithoutUsingNegativeTermsAsKeywords() {
        RequirementAnalysis result = analyzer.analyze(
                "Read GitHub issues and pull requests, add review comments, and never delete repositories or branches."
        );

        assertThat(result.keywords())
                .contains("read", "github", "issues", "pull", "requests", "review", "comments")
                .doesNotContain("never", "delete", "repositories", "branches");
        assertThat(result.structuredRequirement().service()).isEqualTo("github");
        assertThat(result.structuredRequirement().forbiddenCapabilities())
                .containsExactly("github.repository.delete", "github.branch.delete");
    }

    @Test
    void parsesReadOnlyConditionAndExplicitPostgresMutations() {
        RequirementAnalysis result = analyzer.analyze(
                "Query a PostgreSQL database read-only; inserts, updates, deletes, and schema writes are forbidden."
        );

        assertThat(result.keywords()).contains("query", "postgresql", "database", "read")
                .doesNotContain("inserts", "updates", "deletes", "schema", "writes");
        assertThat(result.structuredRequirement().forbiddenCapabilities()).containsExactlyInAnyOrder(
                "postgres.row.insert",
                "postgres.row.update",
                "postgres.row.delete",
                "postgres.schema.write",
                "postgres.row.write"
        );
        assertThat(result.structuredRequirement().constraints()).containsExactly(
                new RequirementConstraint("access-mode", RequirementConstraint.Operator.EQUALS, "read-only")
        );
    }

    @Test
    void parsesSmsOnlyAndNoAuthenticationConditions() {
        RequirementAnalysis twilio = analyzer.analyze(
                "Send Twilio SMS messages, but voice calls must never be available. SMS only."
        );
        RequirementAnalysis docs = analyzer.analyze(
                "Search web documentation and fetch pages as Markdown, read-only and without authentication."
        );

        assertThat(twilio.keywords()).doesNotContain("voice", "calls", "never", "available");
        assertThat(twilio.structuredRequirement().forbiddenCapabilities())
                .containsExactly("twilio.voice.call.create");
        assertThat(twilio.structuredRequirement().constraints()).containsExactly(
                new RequirementConstraint("communication-channel", RequirementConstraint.Operator.EQUALS, "sms")
        );
        assertThat(docs.keywords()).contains("search", "web", "documentation", "fetch", "pages", "markdown", "read")
                .doesNotContain("authentication");
        assertThat(docs.structuredRequirement().constraints()).containsExactly(
                new RequirementConstraint("access-mode", RequirementConstraint.Operator.EQUALS, "read-only"),
                new RequirementConstraint("authentication", RequirementConstraint.Operator.EQUALS, "none")
        );
        assertThat(docs.structuredRequirement().forbiddenCapabilities()).containsExactly(
                "document.publish", "document.edit"
        );
    }
}
