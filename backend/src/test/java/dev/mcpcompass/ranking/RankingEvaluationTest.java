package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.HeuristicRequirementAnalyzer;
import dev.mcpcompass.requirement.RequirementAnalysis;
import dev.mcpcompass.requirement.RequirementConstraint;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RankingEvaluationTest {
    private static final String FIXTURE = "/fixtures/ranking/relevance-evaluation-v1.json";
    private static final Instant FIXED_TIME = Instant.parse("2026-08-18T00:00:00Z");
    private static final Duration LATENCY_GUARD = Duration.ofSeconds(2);

    private final HeuristicRequirementAnalyzer analyzer = new HeuristicRequirementAnalyzer();
    private final RankingService rankingService = new RankingService();

    @Test
    void datasetIsVersionedRepresentativeAndInternallyConsistent() throws IOException {
        Dataset dataset = dataset();
        Set<String> serverIds = new HashSet<>();
        dataset.servers().forEach(server -> assertThat(serverIds.add(server.id())).as(server.id()).isTrue());

        assertThat(dataset.schemaVersion()).isEqualTo("1.0");
        assertThat(dataset.datasetId()).isEqualTo("lexical-relevance-v1");
        assertThat(dataset.queries()).hasSizeGreaterThanOrEqualTo(20);
        assertThat(dataset.queries()).extracting(QueryLabel::domain).doesNotContainNull();
        assertThat(dataset.queries()).allSatisfy(query -> {
            new StructuredRequirement(
                    dataset.schemaVersion(),
                    query.domain(),
                    query.service(),
                    query.requiredCapabilities(),
                    query.forbiddenCapabilities(),
                    query.constraints().stream()
                            .map(constraint -> new RequirementConstraint(
                                    constraint.name(),
                                    RequirementConstraint.Operator.valueOf(constraint.operator()),
                                    constraint.value()
                            ))
                            .toList()
            );

            assertThat(query.notes()).isNotBlank();
            assertThat(query.candidateServerIds()).isNotEmpty().allMatch(serverIds::contains);
            assertThat(query.acceptableServerIds()).allMatch(query.candidateServerIds()::contains);
            assertThat(query.clearlyBadServerIds()).isNotEmpty().allMatch(query.candidateServerIds()::contains);
            assertThat(query.expectNoStrongMatch()).isEqualTo(query.acceptableServerIds().isEmpty());
        });
    }

    @Test
    void lexicalBaselineMatchesTheCheckedInRankingReport() throws IOException {
        Evaluation evaluation = evaluate(dataset());

        System.out.println("Lexical baseline: " + evaluation);

        assertThat(evaluation.labelledQueries()).isEqualTo(18);
        assertThat(evaluation.top1Acceptable()).isEqualTo(16);
        assertThat(evaluation.top3Acceptable()).isEqualTo(18);
        assertThat(evaluation.meanReciprocalRank()).isEqualTo(17.0 / 18.0);
        assertThat(evaluation.expectedNoStrongMatch()).isEqualTo(2);
        assertThat(evaluation.correctNoStrongMatchDecisions()).isEqualTo(20);
        assertThat(evaluation.badMatchesInTop3()).isEqualTo(16);
        assertThat(evaluation.elapsed()).isLessThan(LATENCY_GUARD);
    }

    private Evaluation evaluate(Dataset dataset) {
        Map<String, McpServerEntity> servers = new HashMap<>();
        dataset.servers().forEach(label -> servers.put(label.id(), server(label)));

        int labelledQueries = 0;
        int top1Acceptable = 0;
        int top3Acceptable = 0;
        int correctNoStrongMatchDecisions = 0;
        int expectedNoStrongMatch = 0;
        int badMatchesInTop3 = 0;
        double reciprocalRankSum = 0.0;
        long started = System.nanoTime();

        for (QueryLabel query : dataset.queries()) {
            RequirementAnalysis analysis = analyzer.analyze(query.requirement());
            List<RankedLabel> ranked = query.candidateServerIds().stream()
                    .map(id -> new RankedLabel(id, rankingService.rank(servers.get(id), analysis).score()))
                    .filter(result -> result.score() > 0.0)
                    .sorted((left, right) -> {
                        int scoreOrder = Double.compare(right.score(), left.score());
                        return scoreOrder != 0 ? scoreOrder : left.id().compareTo(right.id());
                    })
                    .toList();

            if (!query.acceptableServerIds().isEmpty()) {
                labelledQueries++;
                int acceptableRank = firstRank(ranked, query.acceptableServerIds());
                if (acceptableRank == 1) {
                    top1Acceptable++;
                } else {
                    System.out.println("Top-1 miss for " + query.id() + ": " + ranked);
                }
                if (acceptableRank > 0 && acceptableRank <= 3) {
                    top3Acceptable++;
                }
                if (acceptableRank > 0) {
                    reciprocalRankSum += 1.0 / acceptableRank;
                }
            } else {
                expectedNoStrongMatch++;
            }

            boolean predictsNoStrongMatch = ranked.isEmpty() || ranked.getFirst().score() < dataset.strongMatchThreshold();
            if (predictsNoStrongMatch == query.expectNoStrongMatch()) {
                correctNoStrongMatchDecisions++;
            }
            if (ranked.stream().limit(3).anyMatch(result -> query.clearlyBadServerIds().contains(result.id()))) {
                badMatchesInTop3++;
            }
        }

        return new Evaluation(
                labelledQueries,
                top1Acceptable,
                top3Acceptable,
                reciprocalRankSum / labelledQueries,
                expectedNoStrongMatch,
                correctNoStrongMatchDecisions,
                badMatchesInTop3,
                Duration.ofNanos(System.nanoTime() - started)
        );
    }

    private static int firstRank(List<RankedLabel> ranked, List<String> acceptableServerIds) {
        for (int index = 0; index < ranked.size(); index++) {
            if (acceptableServerIds.contains(ranked.get(index).id())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static McpServerEntity server(ServerLabel label) {
        McpServerEntity entity = McpServerEntity.create(label.registryName(), FIXED_TIME);
        entity.updateFrom(new RegistryClient.RegistryServerPayload(
                label.registryName(),
                label.title(),
                label.description(),
                "1.0.0",
                label.status(),
                "{}"
        ), FIXED_TIME);
        return entity;
    }

    private static Dataset dataset() throws IOException {
        try (InputStream input = RankingEvaluationTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(input).as(FIXTURE).isNotNull();
            return new ObjectMapper().readValue(input, Dataset.class);
        }
    }

    private record Dataset(
            String schemaVersion,
            String datasetId,
            double strongMatchThreshold,
            List<ServerLabel> servers,
            List<QueryLabel> queries
    ) {
    }

    private record ServerLabel(
            String id,
            String registryName,
            String title,
            String description,
            String status
    ) {
    }

    private record QueryLabel(
            String id,
            String requirement,
            String domain,
            String service,
            List<String> requiredCapabilities,
            List<String> forbiddenCapabilities,
            List<ConstraintLabel> constraints,
            List<String> acceptableServerIds,
            List<String> clearlyBadServerIds,
            List<String> candidateServerIds,
            boolean expectNoStrongMatch,
            String notes
    ) {
    }

    private record ConstraintLabel(String name, String operator, String value) {
    }

    private record RankedLabel(String id, double score) {
    }

    private record Evaluation(
            int labelledQueries,
            int top1Acceptable,
            int top3Acceptable,
            double meanReciprocalRank,
            int expectedNoStrongMatch,
            int correctNoStrongMatchDecisions,
            int badMatchesInTop3,
            Duration elapsed
    ) {
    }
}
