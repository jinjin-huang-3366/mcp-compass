package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.RequirementAnalysis;
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

    private static McpServerEntity server(String name, String title, String description, String status) {
        McpServerEntity entity = McpServerEntity.create(name, Instant.parse("2026-08-10T00:00:00Z"));
        entity.updateFrom(new RegistryClient.RegistryServerPayload(name, title, description, "1.0.0", status, "{}"), Instant.parse("2026-08-10T00:00:00Z"));
        return entity;
    }
}
