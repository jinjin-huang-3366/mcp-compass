package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.embedding.ServerEmbeddingService;
import dev.mcpcompass.ranking.RankingService;
import dev.mcpcompass.ranking.TrustQualitySignalStore;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.registry.RegistrySyncScheduler;
import dev.mcpcompass.registry.RegistrySyncService;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.RequirementAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchRegistryIndependenceTest {
    private static final Set<Class<?>> REGISTRY_REQUEST_TYPES = Set.of(
            RegistryClient.class,
            RegistrySyncService.class,
            RegistrySyncScheduler.class
    );

    @Test
    void userSearchReturnsPersistedCandidatesWithoutRegistryRequestComponents() throws Exception {
        RequirementAnalyzer analyzer = mock(RequirementAnalyzer.class);
        McpServerRepository repository = mock(McpServerRepository.class);
        LexicalCandidateStore lexicalCandidateStore = mock(LexicalCandidateStore.class);
        CapabilityMetadataStore capabilityStore = mock(CapabilityMetadataStore.class);
        ServerEmbeddingService embeddingService = mock(ServerEmbeddingService.class);
        TrustQualitySignalStore trustQualitySignalStore = mock(TrustQualitySignalStore.class);
        McpServerEntity persistedServer = mock(McpServerEntity.class);
        UUID serverId = UUID.fromString("16e45463-617c-4371-8104-3a942c169e2d");
        RequirementAnalysis analysis = new RequirementAnalysis("github issues", List.of("github", "issues"));

        when(analyzer.analyze(analysis.originalRequirement())).thenReturn(analysis);
        when(persistedServer.getId()).thenReturn(serverId);
        when(persistedServer.getRegistryName()).thenReturn("io.example/github");
        when(persistedServer.getTitle()).thenReturn("GitHub Issues MCP");
        when(persistedServer.getDescription()).thenReturn("Read and update repository issues");
        when(persistedServer.getVersion()).thenReturn("1.0.0");
        when(persistedServer.getStatus()).thenReturn("active");
        when(lexicalCandidateStore.findCandidates(analysis.keywords(), 100)).thenReturn(List.of(
                new LexicalCandidateStore.LexicalCandidate(serverId, 1.0)
        ));
        when(repository.findAllById(List.of(serverId))).thenReturn(List.of(persistedServer));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(serverId))).thenReturn(java.util.Map.of());
        when(embeddingService.findNearestServers(analysis.originalRequirement())).thenReturn(List.of());
        when(trustQualitySignalStore.findByServerIds(List.of(serverId))).thenReturn(java.util.Map.of());
        McpSearchService service = new McpSearchService(
                analyzer,
                repository,
                lexicalCandidateStore,
                new RankingService(),
                capabilityStore,
                embeddingService,
                trustQualitySignalStore
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new McpSearchController(service)).build();

        mockMvc.perform(post("/api/v1/mcp/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"github issues"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].id").value(serverId.toString()))
                .andExpect(jsonPath("$.matches[0].registryName").value("io.example/github"))
                .andExpect(jsonPath("$.matches[0].score").value(0.88))
                .andExpect(jsonPath("$.matches[0].qualityScore").value(0.2))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.preAdjustmentScore").value(0.88))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.statusMultiplier").value(1.0))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.contributions[0].feature")
                        .value("retrievalRelevance"))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.contributions[0].contribution")
                        .value(0.85))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.contributions[1].feature")
                        .value("quality"))
                .andExpect(jsonPath("$.matches[0].rankingExplanation.contributions[1].contribution")
                        .value(0.03));
        verify(lexicalCandidateStore).findCandidates(analysis.keywords(), 100);
    }

    @Test
    void userSearchComponentsCannotAcquireRegistryRequestDependencies() {
        assertThat(Stream.of(McpSearchController.class, McpSearchService.class)
                .flatMap(SearchRegistryIndependenceTest::declaredDependencies))
                .noneMatch(REGISTRY_REQUEST_TYPES::contains);
    }

    private static Stream<Class<?>> declaredDependencies(Class<?> component) {
        Stream<Class<?>> fields = Arrays.stream(component.getDeclaredFields()).map(Field::getType);
        Stream<Class<?>> constructorParameters = Arrays.stream(component.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream);
        return Stream.concat(fields, constructorParameters);
    }
}
