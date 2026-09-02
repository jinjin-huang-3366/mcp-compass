package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.embedding.ServerEmbeddingService;
import dev.mcpcompass.ranking.RankingService;
import dev.mcpcompass.ranking.TrustQualitySignalStore;
import dev.mcpcompass.ranking.TrustQualitySignals;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.RequirementAnalyzer;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpSearchServiceTest {
    private final RequirementAnalyzer analyzer = mock(RequirementAnalyzer.class);
    private final McpServerRepository repository = mock(McpServerRepository.class);
    private final LexicalCandidateStore lexicalCandidateStore = mock(LexicalCandidateStore.class);
    private final RankingService rankingService = mock(RankingService.class);
    private final CapabilityMetadataStore capabilityStore = mock(CapabilityMetadataStore.class);
    private final ServerEmbeddingService embeddingService = mock(ServerEmbeddingService.class);
    private final TrustQualitySignalStore trustQualitySignalStore = mock(TrustQualitySignalStore.class);
    private final CandidateEligibilityPolicy eligibilityPolicy = new CandidateEligibilityPolicy();
    private final McpSearchService searchService = new McpSearchService(
            analyzer,
            repository,
            lexicalCandidateStore,
            rankingService,
            capabilityStore,
            embeddingService,
            trustQualitySignalStore,
            eligibilityPolicy,
            new StrongMatchPolicy()
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
        List<LexicalCandidateStore.LexicalCandidate> lexicalCandidates = lexicalCandidates(servers);
        List<UUID> serverIds = servers.stream().map(McpServerEntity::getId).toList();
        when(lexicalCandidateStore.findCandidates(List.of("github"), 100)).thenReturn(lexicalCandidates);
        when(repository.findAllById(serverIds)).thenReturn(servers);
        when(capabilityStore.findCapabilityNamesByServerIds(any())).thenReturn(Map.of());
        when(trustQualitySignalStore.findByServerIds(any())).thenReturn(Map.of());
        servers.forEach(server -> when(rankingService.rank(
                server, analysis, Set.of(), null, TrustQualitySignals.unavailable()))
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
        List<LexicalCandidateStore.LexicalCandidate> lexicalCandidates = lexicalCandidates(List.of(server));
        UUID serverId = server.getId();
        when(lexicalCandidateStore.findCandidates(List.of("github"), 100)).thenReturn(lexicalCandidates);
        when(repository.findAllById(List.of(serverId))).thenReturn(List.of(server));
        when(capabilityStore.findCapabilityNamesByServerIds(any())).thenReturn(Map.of());
        when(trustQualitySignalStore.findByServerIds(any())).thenReturn(Map.of());
        when(rankingService.rank(server, analysis, Set.of(), null, TrustQualitySignals.unavailable()))
                .thenReturn(ranked(server, 0.8));

        SearchResponse response = searchService.search("github", 3, 10);

        assertThat(response.totalMatches()).isEqualTo(1);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.matches()).isEmpty();
    }

    @Test
    void abstainsBelowThresholdAndReturnsParsedIntentAndReason() {
        RequirementAnalysis analysis = new RequirementAnalysis(
                "github issues without repository deletion",
                List.of("github", "issues"),
                new StructuredRequirement(
                        "1.0", "source-control", "github", List.of("github.issue.read"),
                        List.of("github.repository.delete"), List.of()
                )
        );
        McpServerEntity server = server("io.example/issues");
        UUID serverId = server.getId();
        List<LexicalCandidateStore.LexicalCandidate> candidates = lexicalCandidates(List.of(server));
        when(server.getDescription()).thenReturn("Issue tracker");
        when(analyzer.analyze(analysis.originalRequirement())).thenReturn(analysis);
        when(lexicalCandidateStore.findCandidates(analysis.keywords(), 100))
                .thenReturn(candidates);
        when(repository.findAllById(List.of(serverId))).thenReturn(List.of(server));
        when(capabilityStore.findCapabilityNamesByServerIds(any())).thenReturn(Map.of());
        when(trustQualitySignalStore.findByServerIds(any())).thenReturn(Map.of());
        when(rankingService.rank(server, analysis, Set.of(), null, TrustQualitySignals.unavailable()))
                .thenReturn(ranked(server, 0.29));

        SearchResponse response = searchService.search(analysis.originalRequirement(), 1, 10);

        assertThat(response.strongMatch()).isFalse();
        assertThat(response.confidenceThreshold()).isEqualTo(0.3);
        assertThat(response.matches()).isEmpty();
        assertThat(response.totalMatches()).isZero();
        assertThat(response.parsedIntent().service()).isEqualTo("github");
        assertThat(response.parsedIntent().requiredCapabilities()).containsExactly("github.issue.read");
        assertThat(response.parsedIntent().forbiddenCapabilities())
                .containsExactly("github.repository.delete");
        assertThat(response.abstentionReasons()).containsExactly(
                "Best candidate confidence 29% is below the calibrated strong-match threshold of 30%."
        );
    }

    @Test
    void bulkLoadsCapabilitiesAndReturnsCoverageDetails() {
        UUID serverId = UUID.fromString("2d86887d-aa9d-4a4e-9389-41bf89779461");
        McpServerEntity server = server(serverId, "io.example/github");
        when(server.getTitle()).thenReturn("GitHub MCP");
        when(server.getDescription()).thenReturn("GitHub issue tools");
        when(server.getRepositoryUrl()).thenReturn("https://github.com/example/github-mcp");
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
        List<LexicalCandidateStore.LexicalCandidate> lexicalCandidates = lexicalCandidates(List.of(server));
        when(lexicalCandidateStore.findCandidates(analysis.keywords(), 100)).thenReturn(lexicalCandidates);
        when(repository.findAllById(List.of(serverId))).thenReturn(List.of(server));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(serverId)))
                .thenReturn(Map.of(serverId, Set.of("github.issue.read")));
        when(embeddingService.findNearestServers(analysis.originalRequirement())).thenReturn(List.of());
        McpSearchService capabilitySearchService = new McpSearchService(
                analyzer,
                repository,
                lexicalCandidateStore,
                new RankingService(),
                capabilityStore,
                embeddingService,
                trustQualitySignalStore,
                eligibilityPolicy,
                new StrongMatchPolicy()
        );

        SearchResponse response = capabilitySearchService.search(analysis.originalRequirement(), 1, 10);

        assertThat(response.matches()).singleElement().satisfies(match -> {
            assertThat(match.repositoryUrl()).isEqualTo("https://github.com/example/github-mcp");
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
        List<LexicalCandidateStore.LexicalCandidate> lexicalCandidates = lexicalCandidates(List.of(lexical));
        when(lexicalCandidateStore.findCandidates(analysis.keywords(), 100)).thenReturn(lexicalCandidates);
        when(embeddingService.findNearestServers(analysis.originalRequirement()))
                .thenReturn(List.of(
                        new ServerEmbeddingService.ServerEmbeddingMatch(lexicalId, 0.9),
                        new ServerEmbeddingService.ServerEmbeddingMatch(vectorId, 0.8)
                ));
        when(repository.findAllById(List.of(lexicalId, vectorId))).thenReturn(List.of(vector, lexical));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(lexicalId, vectorId)))
                .thenReturn(Map.of());
        when(trustQualitySignalStore.findByServerIds(any())).thenReturn(Map.of());
        McpSearchService vectorSearchService = new McpSearchService(
                analyzer,
                repository,
                lexicalCandidateStore,
                new RankingService(),
                capabilityStore,
                embeddingService,
                trustQualitySignalStore,
                eligibilityPolicy,
                new StrongMatchPolicy()
        );

        SearchResponse response = vectorSearchService.search(analysis.originalRequirement(), 1, 10);

        assertThat(response.matches()).extracting(SearchResponse.Match::id)
                .containsExactlyInAnyOrder(lexicalId, vectorId);
        assertThat(response.matches())
                .filteredOn(match -> match.id().equals(vectorId))
                .singleElement()
                .satisfies(match -> assertThat(match.reasons()).contains("semantic similarity 80%"));
    }

    @Test
    void excludesForbiddenCandidateBeforeRankingAndReturnsReasons() {
        UUID serverId = UUID.fromString("d48c198b-1f1d-48fa-9248-f5054461b927");
        McpServerEntity server = server(serverId, "io.example/twilio", "Twilio MCP");
        when(server.getDescription()).thenReturn("Send SMS and make voice calls");
        RequirementAnalysis analysis = new RequirementAnalysis(
                "Send Twilio SMS, but never make voice calls",
                List.of("send", "twilio", "sms"),
                new StructuredRequirement(
                        "1.0", "communication", "twilio", List.of("twilio.sms.send"),
                        List.of("twilio.voice.call.create"), List.of()
                )
        );
        when(analyzer.analyze(analysis.originalRequirement())).thenReturn(analysis);
        List<LexicalCandidateStore.LexicalCandidate> candidates = lexicalCandidates(List.of(server));
        when(lexicalCandidateStore.findCandidates(analysis.keywords(), 100)).thenReturn(candidates);
        when(repository.findAllById(List.of(serverId))).thenReturn(List.of(server));
        when(capabilityStore.findCapabilityNamesByServerIds(List.of(serverId))).thenReturn(Map.of());
        when(trustQualitySignalStore.findByServerIds(List.of(serverId))).thenReturn(Map.of());

        SearchResponse response = searchService.search(analysis.originalRequirement(), 1, 10);

        assertThat(response.totalMatches()).isZero();
        assertThat(response.totalExcluded()).isEqualTo(1);
        assertThat(response.exclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.registryName()).isEqualTo("io.example/twilio");
            assertThat(exclusion.reasons()).containsExactly(
                    "forbidden capability advertised: twilio.voice.call.create (Registry metadata)"
            );
        });
        verify(rankingService, never()).rank(any(), any(), any(), any(), any());
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

    private static McpServerEntity server(UUID id, String registryName, String title) {
        McpServerEntity server = server(id, registryName);
        when(server.getTitle()).thenReturn(title);
        when(server.getDescription()).thenReturn("GitHub issue tools");
        when(server.getVersion()).thenReturn("1.0.0");
        when(server.getStatus()).thenReturn("active");
        return server;
    }

    private static RankingService.RankedServer ranked(McpServerEntity server, double score) {
        return new RankingService.RankedServer(
                server,
                score,
                0.2,
                null,
                List.of(),
                List.of(),
                new RankingService.RankingExplanation(
                        List.of(new RankingService.RankingFeatureContribution(
                                "retrievalRelevance", 0.8, 1.0, 0.8
                        )),
                        0.8,
                        1.0
                ),
                List.of("text match")
        );
    }

    private static List<LexicalCandidateStore.LexicalCandidate> lexicalCandidates(
            List<McpServerEntity> servers
    ) {
        return servers.stream()
                .map(server -> new LexicalCandidateStore.LexicalCandidate(server.getId(), 1.0))
                .toList();
    }
}
