package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.embedding.ServerEmbeddingService;
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
    private final CapabilityMetadataStore capabilityStore = mock(CapabilityMetadataStore.class);
    private final ServerEmbeddingService embeddingService = mock(ServerEmbeddingService.class);
    private final McpSearchService searchService = new McpSearchService(
            analyzer,
            repository,
            new RankingService(),
            capabilityStore,
            embeddingService
    );

    @Test
    void bulkLoadsCapabilitiesAndReturnsCoverageDetails() {
        UUID serverId = UUID.fromString("2d86887d-aa9d-4a4e-9389-41bf89779461");
        McpServerEntity server = mock(McpServerEntity.class);
        when(server.getId()).thenReturn(serverId);
        when(server.getRegistryName()).thenReturn("io.example/github");
        when(server.getTitle()).thenReturn("GitHub MCP");
        when(server.getDescription()).thenReturn("GitHub issue tools");
        when(server.getVersion()).thenReturn("1.0.0");
        when(server.getStatus()).thenReturn("active");
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
        ))
                .thenReturn(new PageImpl<>(List.of(server)));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(serverId)))
                .thenReturn(Map.of(serverId, Set.of("github.issue.read")));
        when(embeddingService.findNearestServers(analysis.originalRequirement())).thenReturn(List.of());

        SearchResponse response = searchService.search(analysis.originalRequirement());

        assertThat(response.matches()).singleElement().satisfies(match -> {
            assertThat(match.capabilityCoverage()).isEqualTo(0.5);
            assertThat(match.matchedCapabilities()).containsExactly("github.issue.read");
            assertThat(match.missingCapabilities()).containsExactly("github.issue.create");
        });
        verify(capabilityStore).findCapabilityNamesByServerIds(List.of(serverId));
    }

    @Test
    void mergesVectorCandidatesAfterLexicalCandidatesWithoutDuplicates() {
        UUID lexicalId = UUID.fromString("7303171c-33b8-44f2-9a2a-42d38675b054");
        UUID vectorId = UUID.fromString("832cfe99-4e9f-45fc-9fa4-ed9bc6b2f3ef");
        McpServerEntity lexical = server(lexicalId, "io.example/github", "GitHub MCP");
        McpServerEntity vector = server(vectorId, "io.example/issues", "Issue tracker");
        RequirementAnalysis analysis = new RequirementAnalysis("github issues", List.of("github", "issues"));
        when(analyzer.analyze(analysis.originalRequirement())).thenReturn(analysis);
        when(repository.findAll(
                org.mockito.ArgumentMatchers.<Specification<McpServerEntity>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(lexical)));
        when(embeddingService.findNearestServers(analysis.originalRequirement()))
                .thenReturn(List.of(
                        new ServerEmbeddingService.ServerEmbeddingMatch(lexicalId, 0.9),
                        new ServerEmbeddingService.ServerEmbeddingMatch(vectorId, 0.8)
                ));
        when(repository.findAllById(List.of(lexicalId, vectorId))).thenReturn(List.of(vector, lexical));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(lexicalId, vectorId)))
                .thenReturn(Map.of());

        SearchResponse response = searchService.search(analysis.originalRequirement());

        assertThat(response.matches()).extracting(SearchResponse.Match::id)
                .containsExactlyInAnyOrder(lexicalId, vectorId);
        assertThat(response.matches())
                .filteredOn(match -> match.id().equals(vectorId))
                .singleElement()
                .satisfies(match -> assertThat(match.reasons()).contains("semantic similarity 80%"));
    }

    private static McpServerEntity server(UUID id, String registryName, String title) {
        McpServerEntity server = mock(McpServerEntity.class);
        when(server.getId()).thenReturn(id);
        when(server.getRegistryName()).thenReturn(registryName);
        when(server.getTitle()).thenReturn(title);
        when(server.getDescription()).thenReturn("GitHub issue tools");
        when(server.getVersion()).thenReturn("1.0.0");
        when(server.getStatus()).thenReturn("active");
        return server;
    }
}
