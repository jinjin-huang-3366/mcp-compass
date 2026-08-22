package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.ranking.RankingService;
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

    public McpSearchService(
            RequirementAnalyzer analyzer,
            McpServerRepository repository,
            RankingService rankingService,
            CapabilityMetadataStore capabilityStore
    ) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.rankingService = rankingService;
        this.capabilityStore = capabilityStore;
    }

    public SearchResponse search(String requirement, int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Page and page size must be positive");
        }

        RequirementAnalysis analysis = analyzer.analyze(requirement);
        if (analysis.keywords().isEmpty()) {
            return new SearchResponse(requirement, List.of(), page, pageSize, 0, 0, List.of());
        }

        List<McpServerEntity> candidates = repository.findAll(
                candidateSpec(analysis.keywords()),
                PageRequest.of(0, MAX_CANDIDATES)
        ).getContent();
        Map<UUID, Set<String>> capabilitiesByServer = capabilityStore.findCapabilityNamesByServerIds(
                candidates.stream().map(McpServerEntity::getId).toList()
        );

        List<RankingService.RankedServer> rankedMatches = candidates.stream()
                .filter(server -> !"deleted".equalsIgnoreCase(server.getStatus()))
                .map(server -> rankingService.rank(
                        server,
                        analysis,
                        capabilitiesByServer.getOrDefault(server.getId(), Set.of())
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
                        ranked.capabilityCoverage() == null ? null : rounded(ranked.capabilityCoverage()),
                        ranked.matchedCapabilities(),
                        ranked.missingCapabilities(),
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
