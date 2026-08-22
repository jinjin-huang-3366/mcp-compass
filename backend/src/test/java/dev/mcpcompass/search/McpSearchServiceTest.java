package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.ranking.RankingService;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.RequirementAnalyzer;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSearchServiceTest {
    private final RequirementAnalyzer analyzer = mock(RequirementAnalyzer.class);
    private final McpServerRepository repository = mock(McpServerRepository.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final CapabilityMetadataStore capabilityStore = mock(CapabilityMetadataStore.class);
    private final McpSearchService searchService = new McpSearchService(
            analyzer,
            repository,
            rankingService,
            capabilityStore
    );

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
        )).thenReturn(new PageImpl<>(servers));
        when(capabilityStore.findCapabilityNamesByServerIds(any())).thenReturn(Map.of());
        servers.forEach(server -> when(rankingService.rank(server, analysis, Set.of()))
                .thenReturn(ranked(server, 0.8)));

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
        )).thenReturn(new PageImpl<>(List.of(server)));
        when(capabilityStore.findCapabilityNamesByServerIds(any())).thenReturn(Map.of());
        when(rankingService.rank(server, analysis, Set.of())).thenReturn(ranked(server, 0.8));

        SearchResponse response = searchService.search("github", 3, 10);

        assertThat(response.totalMatches()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void bulkLoadsCapabilitiesAndReturnsCoverageDetails() {
        UUID serverId = UUID.fromString("2d86887d-aa9d-4a4e-9389-41bf89779461");
        McpServerEntity server = server(serverId, "io.example/github");
        when(server.getTitle()).thenReturn("GitHub MCP");
        when(server.getDescription()).thenReturn("GitHub issue tools");
        RequirementAnalysis analysis = new RequirementAnalysis(
                "read and create GitHub issues",
                List.of("github", "issues"),
                new StructuredRequirement(
                        "1.0",
                        "source-control",
                        "github",
                        List.of("github.issue.read", "github.issue.create"),
                        List.of(),
                        List.of()
                )
        );
        when(analyzer.analyze(analysis.originalRequirement())).thenReturn(analysis);
        when(repository.findAll(
                org.mockito.ArgumentMatchers.<Specification<McpServerEntity>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(server)));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(serverId)))
                .thenReturn(Map.of(serverId, Set.of("github.issue.read")));
        McpSearchService capabilitySearchService = new McpSearchService(
                analyzer,
                repository,
                new RankingService(),
                capabilityStore
        );

        SearchResponse response = capabilitySearchService.search(analysis.originalRequirement(), 1, 10);

        assertThat(response.matches()).singleElement().satisfies(match -> {
            assertThat(match.capabilityCoverage()).isEqualTo(0.5);
            assertThat(match.matchedCapabilities()).containsExactly("github.issue.read");
            assertThat(match.missingCapabilities()).containsExactly("github.issue.create");
        });
        verify(capabilityStore).findCapabilityNamesByServerIds(List.of(serverId));
    }

    private static McpServerEntity server(String registryName) {
        UUID id = UUID.nameUUIDFromBytes(registryName.getBytes(StandardCharsets.UTF_8));
        return server(id, registryName);
    }

    private static McpServerEntity server(UUID id, String registryName) {
        McpServerEntity server = mock(McpServerEntity.class);
        when(server.getId()).thenReturn(id);
        when(server.getRegistryName()).thenReturn(registryName);
        when(server.getTitle()).thenReturn("GitHub MCP");
        when(server.getDescription()).thenReturn("GitHub tools");
        when(server.getVersion()).thenReturn("1.0.0");
        when(server.getStatus()).thenReturn("active");
        return server;
    }

    private static RankingService.RankedServer ranked(McpServerEntity server, double score) {
        return new RankingService.RankedServer(
                server,
                score,
                null,
                List.of(),
                List.of(),
                List.of("text match")
        );
    }
}
