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
    private final McpSearchService searchService = new McpSearchService(
            analyzer,
            repository,
            new RankingService(),
            capabilityStore
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

        SearchResponse response = searchService.search(analysis.originalRequirement());

        assertThat(response.matches()).singleElement().satisfies(match -> {
            assertThat(match.capabilityCoverage()).isEqualTo(0.5);
            assertThat(match.matchedCapabilities()).containsExactly("github.issue.read");
            assertThat(match.missingCapabilities()).containsExactly("github.issue.create");
        });
        verify(capabilityStore).findCapabilityNamesByServerIds(List.of(serverId));
    }
}
