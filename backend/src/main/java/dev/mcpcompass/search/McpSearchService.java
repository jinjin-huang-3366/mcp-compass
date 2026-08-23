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
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
    private final RankingService rankingService;
    private final CapabilityMetadataStore capabilityStore;
    private final ServerEmbeddingService embeddingService;
    private final TrustQualitySignalStore trustQualitySignalStore;

    public McpSearchService(
            RequirementAnalyzer analyzer,
            McpServerRepository repository,
            RankingService rankingService,
            CapabilityMetadataStore capabilityStore,
            ServerEmbeddingService embeddingService,
            TrustQualitySignalStore trustQualitySignalStore
    ) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.rankingService = rankingService;
        this.capabilityStore = capabilityStore;
        this.embeddingService = embeddingService;
        this.trustQualitySignalStore = trustQualitySignalStore;
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

        List<RankingService.RankedServer> rankedMatches = candidates.stream()
                .filter(candidate -> !"deleted".equalsIgnoreCase(candidate.server().getStatus()))
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
            repository.findAll(candidateSpec(keywords), PageRequest.of(0, MAX_CANDIDATES)).getContent()
                    .forEach(server -> candidatesById.put(
                            server.getId(),
                            new RetrievedCandidate(server, null)
                    ));
        }

        List<ServerEmbeddingService.ServerEmbeddingMatch> vectorMatches =
                embeddingService.findNearestServers(requirement);
        List<UUID> vectorIds = vectorMatches.stream()
                .map(ServerEmbeddingService.ServerEmbeddingMatch::serverId)
                .toList();
        Map<UUID, McpServerEntity> vectorCandidatesById = new java.util.HashMap<>();
        repository.findAllById(vectorIds)
                .forEach(server -> vectorCandidatesById.put(server.getId(), server));
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

    private static Specification<McpServerEntity> candidateSpec(List<String> keywords) {
        return (root, query, cb) -> {
            List<Predicate> matches = new ArrayList<>();
            for (String keyword : keywords) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                matches.add(cb.like(cb.lower(root.<String>get("registryName")), pattern));
                matches.add(cb.like(cb.lower(cb.coalesce(root.<String>get("title"), "")), pattern));
                matches.add(cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern));
            }
            Predicate textMatch = cb.or(matches.toArray(Predicate[]::new));
            Predicate notDeleted = cb.or(cb.isNull(root.<String>get("status")), cb.notEqual(cb.lower(root.<String>get("status")), "deleted"));
            return cb.and(textMatch, notDeleted);
        };
    }

    private static double rounded(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
