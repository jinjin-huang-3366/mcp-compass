package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankingServiceTest {
    private final RankingService rankingService = new RankingService();

    @Test
    void titleMatchScoresHigherThanDescriptionOnlyMatch() {
        RequirementAnalysis requirement = new RequirementAnalysis("github issues", List.of("github", "issues"));

        McpServerEntity titleMatch = server("io.example/a", "GitHub Issues MCP", "tools for repositories", "active");
        McpServerEntity descriptionMatch = server("io.example/b", "Developer MCP", "github issues support", "active");

        assertThat(rankingService.rank(titleMatch, requirement).score())
                .isGreaterThan(rankingService.rank(descriptionMatch, requirement).score());
    }

    @Test
    void deprecatedServerIsPenalized() {
        RequirementAnalysis requirement = new RequirementAnalysis("github", List.of("github"));
        McpServerEntity active = server("io.example/a", "GitHub MCP", "", "active");
        McpServerEntity deprecated = server("io.example/b", "GitHub MCP", "", "deprecated");

        assertThat(rankingService.rank(active, requirement).score())
                .isGreaterThan(rankingService.rank(deprecated, requirement).score());
    }

    @Test
    void capabilityCoverageDominatesTextOverlap() {
        RequirementAnalysis requirement = structuredRequirement(
                List.of("github", "issues"),
                List.of("github.issue.read", "github.issue.comment.create")
        );
        McpServerEntity perfectTextOnly = server(
                "github-issues",
                "GitHub Issues",
                "github issues",
                "active"
        );
        McpServerEntity fullCapabilityCoverage = server(
                "io.example/capable",
                "Developer tools",
                "",
                "active"
        );

        RankingService.RankedServer textOnlyRank = rankingService.rank(perfectTextOnly, requirement, List.of());
        RankingService.RankedServer capabilityRank = rankingService.rank(
                fullCapabilityCoverage,
                requirement,
                List.of("github.issue.read", "github.issue.comment.create")
        );

        assertThat(capabilityRank.score()).isGreaterThan(textOnlyRank.score());
        assertThat(capabilityRank.capabilityCoverage()).isEqualTo(1.0);
        assertThat(textOnlyRank.capabilityCoverage()).isEqualTo(0.0);
    }

    @Test
    void reportsMatchedAndMissingCapabilitiesInRequirementOrder() {
        RequirementAnalysis requirement = structuredRequirement(
                List.of("github"),
                List.of("github.issue.read", "github.pull-request.create")
        );
        McpServerEntity server = server("io.example/github", "GitHub MCP", "", "active");

        RankingService.RankedServer ranked = rankingService.rank(
                server,
                requirement,
                List.of("GITHUB.ISSUE.READ", "unrelated.capability")
        );

        assertThat(ranked.capabilityCoverage()).isEqualTo(0.5);
        assertThat(ranked.matchedCapabilities()).containsExactly("github.issue.read");
        assertThat(ranked.missingCapabilities()).containsExactly("github.pull-request.create");
        assertThat(ranked.reasons()).contains("capability coverage 1/2");
    }

    @Test
    void fallsBackToTextScoreWhenRequirementHasNoCapabilities() {
        RequirementAnalysis requirement = new RequirementAnalysis("github", List.of("github"));
        McpServerEntity server = server("io.example/github", "GitHub MCP", "", "active");

        RankingService.RankedServer ranked = rankingService.rank(server, requirement, List.of("github.issue.read"));

        assertThat(ranked.score()).isEqualTo(1.0);
        assertThat(ranked.capabilityCoverage()).isNull();
        assertThat(ranked.matchedCapabilities()).isEmpty();
        assertThat(ranked.missingCapabilities()).isEmpty();
    }

    private static RequirementAnalysis structuredRequirement(List<String> keywords, List<String> capabilities) {
        return new RequirementAnalysis(
                "github requirement",
                keywords,
                new StructuredRequirement("1.0", "source-control", "github", capabilities, List.of(), List.of())
        );
    }

    private static McpServerEntity server(String name, String title, String description, String status) {
        McpServerEntity entity = McpServerEntity.create(name, Instant.parse("2026-08-10T00:00:00Z"));
        entity.updateFrom(new RegistryClient.RegistryServerPayload(name, title, description, "1.0.0", status, "{}"), Instant.parse("2026-08-10T00:00:00Z"));
        return entity;
    }
}
