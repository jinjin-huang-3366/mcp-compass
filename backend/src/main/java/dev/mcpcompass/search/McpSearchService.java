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
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class McpSearchService {
    private static final int MAX_CANDIDATES = 100;

    private final RequirementAnalyzer analyzer;
    private final McpServerRepository repository;
    private final LexicalCandidateStore lexicalCandidateStore;
    private final RankingService rankingService;
    private final CapabilityMetadataStore capabilityStore;
    private final ServerEmbeddingService embeddingService;
    private final TrustQualitySignalStore trustQualitySignalStore;
    private final CandidateEligibilityPolicy eligibilityPolicy;

    public McpSearchService(
            RequirementAnalyzer analyzer,
            McpServerRepository repository,
            LexicalCandidateStore lexicalCandidateStore,
            RankingService rankingService,
            CapabilityMetadataStore capabilityStore,
            ServerEmbeddingService embeddingService,
            TrustQualitySignalStore trustQualitySignalStore,
            CandidateEligibilityPolicy eligibilityPolicy
    ) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.lexicalCandidateStore = lexicalCandidateStore;
        this.rankingService = rankingService;
        this.capabilityStore = capabilityStore;
        this.embeddingService = embeddingService;
        this.trustQualitySignalStore = trustQualitySignalStore;
        this.eligibilityPolicy = eligibilityPolicy;
    }

    public SearchResponse search(String requirement, int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }

        RequirementAnalysis analysis = analyzer.analyze(requirement);
        List<RetrievedCandidate> candidates = candidates(requirement, analysis.keywords());
        Map<UUID, Set<String>> capabilitiesByServer = capabilityStore.findCapabilityNamesByServerIds(
                candidates.stream().map(candidate -> candidate.server().getId()).toList()
        );
        Map<UUID, TrustQualitySignals> trustSignalsByServer = trustQualitySignalStore.findByServerIds(
                candidates.stream().map(candidate -> candidate.server().getId()).toList()
        );

        List<SearchResponse.Exclusion> exclusions = new ArrayList<>();
        List<RankingService.RankedServer> rankedMatches = candidates.stream()
                .filter(candidate -> !"deleted".equalsIgnoreCase(candidate.server().getStatus()))
                .filter(candidate -> {
                    CandidateEligibilityPolicy.Eligibility eligibility = eligibilityPolicy.evaluate(
                            analysis.structuredRequirement(),
                            candidate.server(),
                            capabilitiesByServer.getOrDefault(candidate.server().getId(), Set.of())
                    );
                    if (!eligibility.eligible()) {
                        exclusions.add(new SearchResponse.Exclusion(
                                candidate.server().getId(),
                                candidate.server().getRegistryName(),
                                candidate.server().getTitle(),
                                eligibility.reasons()
                        ));
                    }
                    return eligibility.eligible();
                })
                .map(candidate -> rankingService.rank(
                        candidate.server(),
                        analysis,
                        capabilitiesByServer.getOrDefault(candidate.server().getId(), Set.of()),
                        candidate.vectorSimilarity(),
                        trustSignalsByServer.getOrDefault(
                                candidate.server().getId(), TrustQualitySignals.unavailable()
                        )
                ))
                .filter(ranked -> ranked.score() > 0)
                .sorted(Comparator.comparingDouble(RankingService.RankedServer::score)
                        .reversed()
                        .thenComparing(ranked -> ranked.server().getRegistryName()))
                .toList();

        int totalMatches = rankedMatches.size();
        int totalPages = totalMatches == 0 ? 0 : (totalMatches + pageSize - 1) / pageSize;
        int fromIndex = (int) Math.min((long) (page - 1) * pageSize, totalMatches);
        int toIndex = Math.min(fromIndex + pageSize, totalMatches);

        List<SearchResponse.Match> matches = rankedMatches.subList(fromIndex, toIndex).stream()
                .map(ranked -> new SearchResponse.Match(
                        ranked.server().getId(),
                        ranked.server().getRegistryName(),
                        ranked.server().getTitle(),
                        ranked.server().getDescription(),
                        ranked.server().getVersion(),
                        ranked.server().getStatus(),
                        ranked.server().getRepositoryUrl(),
                        rounded(ranked.score()),
                        rounded(ranked.qualityScore()),
                        ranked.capabilityCoverage() == null ? null : rounded(ranked.capabilityCoverage()),
                        ranked.matchedCapabilities(),
                        ranked.missingCapabilities(),
                        rankingExplanation(ranked.rankingExplanation()),
                        ranked.reasons()
                ))
                .toList();

        return new SearchResponse(
                requirement,
                analysis.keywords(),
                page,
                pageSize,
                totalMatches,
                totalPages,
                exclusions.size(),
                exclusions.stream()
                        .sorted(Comparator.comparing(SearchResponse.Exclusion::registryName))
                        .toList(),
                matches
        );
    }

    private static SearchResponse.RankingExplanation rankingExplanation(
            RankingService.RankingExplanation explanation
    ) {
        return new SearchResponse.RankingExplanation(
                explanation.contributions().stream()
                        .map(contribution -> new SearchResponse.RankingFeatureContribution(
                                contribution.feature(),
                                rounded(contribution.featureScore()),
                                rounded(contribution.weight()),
                                rounded(contribution.contribution())
                        ))
                        .toList(),
                rounded(explanation.preAdjustmentScore()),
                rounded(explanation.statusMultiplier())
        );
    }

    private List<RetrievedCandidate> candidates(String requirement, List<String> keywords) {
        Map<UUID, RetrievedCandidate> candidatesById = new LinkedHashMap<>();
        if (!keywords.isEmpty()) {
            List<UUID> lexicalIds = lexicalCandidateStore.findCandidates(keywords, MAX_CANDIDATES).stream()
                    .map(LexicalCandidateStore.LexicalCandidate::serverId)
                    .toList();
            Map<UUID, McpServerEntity> lexicalCandidatesById = serversById(lexicalIds);
            lexicalIds.forEach(serverId -> {
                McpServerEntity server = lexicalCandidatesById.get(serverId);
                if (server != null) {
                    candidatesById.put(serverId, new RetrievedCandidate(server, null));
                }
            });
        }

        List<ServerEmbeddingService.ServerEmbeddingMatch> vectorMatches =
                embeddingService.findNearestServers(requirement);
        List<UUID> vectorIds = vectorMatches.stream()
                .map(ServerEmbeddingService.ServerEmbeddingMatch::serverId)
                .toList();
        Map<UUID, McpServerEntity> vectorCandidatesById = serversById(vectorIds);
        vectorMatches.forEach(match -> {
            McpServerEntity server = vectorCandidatesById.get(match.serverId());
            if (server != null) {
                candidatesById.put(
                        match.serverId(),
                        new RetrievedCandidate(server, match.similarity())
                );
            }
        });
        return List.copyOf(candidatesById.values());
    }

    private record RetrievedCandidate(McpServerEntity server, Double vectorSimilarity) {
    }

    private Map<UUID, McpServerEntity> serversById(List<UUID> serverIds) {
        Map<UUID, McpServerEntity> serversById = new java.util.HashMap<>();
        if (!serverIds.isEmpty()) {
            repository.findAllById(serverIds).forEach(server -> serversById.put(server.getId(), server));
        }
        return serversById;
    }

    private static double rounded(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
