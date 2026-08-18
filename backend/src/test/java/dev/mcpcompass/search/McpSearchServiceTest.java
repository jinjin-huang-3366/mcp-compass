package dev.mcpcompass.search;

import dev.mcpcompass.ranking.RankingService;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.RequirementAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpSearchServiceTest {

    private final RequirementAnalyzer analyzer = mock(RequirementAnalyzer.class);
    private final McpServerRepository repository = mock(McpServerRepository.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final McpSearchService searchService = new McpSearchService(analyzer, repository, rankingService);

    @Test
    void searchRequestDefaultsToFirstPageOfTenResults() {
        SearchRequest request = new SearchRequest("github", null, null);

        assertThat(request.effectivePage()).isEqualTo(1);
        assertThat(request.effectivePageSize()).isEqualTo(10);
    }

    @Test
    void returnsRequestedPageWithStableRankingMetadata() {
        RequirementAnalysis analysis = new RequirementAnalysis("github", List.of("github"));
        List<McpServerEntity> servers = List.of(
                server("io.example/e"),
                server("io.example/d"),
                server("io.example/c"),
                server("io.example/b"),
                server("io.example/a")
        );
        when(analyzer.analyze("github")).thenReturn(analysis);
        when(repository.findAll(
                org.mockito.ArgumentMatchers.<Specification<McpServerEntity>>any(),
                any(Pageable.class)
        ))
                .thenReturn(new PageImpl<>(servers));
        servers.forEach(server -> when(rankingService.rank(server, analysis))
                .thenReturn(new RankingService.RankedServer(server, 0.8, List.of("text match"))));

        SearchResponse response = searchService.search("github", 2, 2);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.totalMatches()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.matches())
                .extracting(SearchResponse.Match::registryName)
                .containsExactly("io.example/c", "io.example/d");
    }

    @Test
    void returnsEmptyPageWhenPageIsPastAvailableResults() {
        RequirementAnalysis analysis = new RequirementAnalysis("github", List.of("github"));
        McpServerEntity server = server("io.example/a");
        when(analyzer.analyze("github")).thenReturn(analysis);
        when(repository.findAll(
                org.mockito.ArgumentMatchers.<Specification<McpServerEntity>>any(),
                any(Pageable.class)
        ))
                .thenReturn(new PageImpl<>(List.of(server)));
        when(rankingService.rank(server, analysis))
                .thenReturn(new RankingService.RankedServer(server, 0.8, List.of("text match")));

        SearchResponse response = searchService.search("github", 3, 10);

        assertThat(response.totalMatches()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.matches()).isEmpty();
    }

    private static McpServerEntity server(String registryName) {
        Instant seenAt = Instant.parse("2026-08-10T00:00:00Z");
        McpServerEntity entity = McpServerEntity.create(registryName, seenAt);
        entity.updateFrom(new RegistryClient.RegistryServerPayload(
                registryName,
                "GitHub MCP",
                "GitHub tools",
                "1.0.0",
                "active",
                "{}"
        ), seenAt);
        return entity;
    }
}
