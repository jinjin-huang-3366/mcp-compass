package dev.mcpcompass.ranking;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.HeuristicRequirementAnalyzer;
import dev.mcpcompass.requirement.RequirementAnalysis;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegistryRelevanceEvaluationTest {
    private static final String SNAPSHOT = "/fixtures/ranking/registry-snapshot-2026-08-31.json";
    private static final String LABELS = "/fixtures/ranking/registry-relevance-v1.json";
    private static final int RETRIEVAL_LIMIT = 100;
    private static final int RANKING_CUTOFF = 10;
    private static final int ACCEPTABILITY_CUTOFF = 3;
    private static final Duration LATENCY_GUARD = Duration.ofSeconds(2);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HeuristicRequirementAnalyzer analyzer = new HeuristicRequirementAnalyzer();
    private final RankingService rankingService = new RankingService();

    @Test
    void corpusIsVersionedProductionGroundedAndInternallyConsistent() throws IOException {
        RegistrySnapshot snapshot = read(SNAPSHOT, RegistrySnapshot.class);
        Dataset dataset = read(LABELS, Dataset.class);
        Set<String> serverNames = new HashSet<>();
        snapshot.servers().forEach(server -> {
            assertThat(serverNames.add(server.name())).as(server.name()).isTrue();
            assertThat(server.name()).isNotBlank();
            assertThat(server.description()).isNotBlank();
            assertThat(server.version()).isNotBlank();
            assertThat(server.status()).isIn("active", "deprecated");
            assertThat(server.updatedAt()).isNotNull();
        });

        assertThat(snapshot.schemaVersion()).isEqualTo("1.0");
        assertThat(snapshot.snapshotId()).isEqualTo("official-registry-2026-08-31-rel01");
        assertThat(snapshot.recordedAt()).isEqualTo(Instant.parse("2026-08-31T16:15:00Z"));
        assertThat(snapshot.source()).isEqualTo("https://registry.modelcontextprotocol.io/v0.1/servers");
        assertThat(snapshot.recording()).contains("official Registry").contains("untrusted");
        assertThat(snapshot.sourceRequests()).hasSizeGreaterThanOrEqualTo(6)
                .allMatch(request -> request.startsWith("/v0.1/servers"));
        assertThat(snapshot.servers()).hasSizeGreaterThanOrEqualTo(25);

        assertThat(dataset.schemaVersion()).isEqualTo("1.0");
        assertThat(dataset.datasetId()).isEqualTo("registry-relevance-v1");
        assertThat(dataset.snapshotId()).isEqualTo(snapshot.snapshotId());
        assertThat(dataset.queries()).hasSize(32);
        assertThat(dataset.queries()).extracting(QueryLabel::cohort)
                .contains("github", "twilio", "postgres", "web-docs", "no-match");
        assertThat(dataset.queries()).extracting(QueryLabel::id)
                .contains("github-no-delete", "twilio-sms-no-voice", "postgres-read-only", "web-docs-readonly");

        dataset.queries().forEach(query -> {
            assertThat(query.id()).isNotBlank();
            assertThat(query.requirement()).isNotBlank();
            assertThat(query.requiredCapabilities()).isNotEmpty();
            assertThat(query.notes()).isNotBlank();
            assertThat(query.relevance().keySet()).allMatch(serverNames::contains);
            assertThat(query.relevance().values()).allMatch(grade -> grade >= 1 && grade <= 3);
            assertThat(query.acceptableServerIds()).allMatch(query.relevance()::containsKey);
            assertThat(query.forbiddenServerIds()).isNotEmpty().allMatch(serverNames::contains);
            assertThat(query.acceptableServerIds()).doesNotContainAnyElementsOf(query.forbiddenServerIds());
            assertThat(query.expectedAbstain()).isEqualTo(query.acceptableServerIds().isEmpty());
        });
    }

    @Test
    void registryBaselineMatchesCheckedInReport() throws IOException {
        Evaluation evaluation = evaluate(read(SNAPSHOT, RegistrySnapshot.class), read(LABELS, Dataset.class));

        System.out.println("REL-01 Registry baseline: " + evaluation.summary());
        evaluation.results().forEach(result -> System.out.println(
                "%s top3=%s retrievedRelevant=%d/%d acceptableRank=%d abstain=%s expected=%s forbidden=%s"
                        .formatted(
                                result.queryId(),
                                result.ranked().stream().limit(ACCEPTABILITY_CUTOFF).toList(),
                                result.retrievedRelevant(),
                                result.totalRelevant(),
                                result.acceptableRank(),
                                result.predictedAbstain(),
                                result.expectedAbstain(),
                                result.forbiddenInTopThree()
                        )
        ));

        assertThat(evaluation.labelledQueries()).isEqualTo(26);
        assertThat(evaluation.noMatchQueries()).isEqualTo(6);
        assertThat(rounded(evaluation.recallAt100())).isEqualTo(0.9623);
        assertThat(rounded(evaluation.ndcgAt10())).isEqualTo(0.9433);
        assertThat(evaluation.acceptabilityQueries()).isEqualTo(24);
        assertThat(evaluation.topThreeAcceptable()).isEqualTo(24);
        assertThat(evaluation.forbiddenViolations()).isEqualTo(13);
        assertThat(evaluation.expectedAbstentions()).isEqualTo(8);
        assertThat(evaluation.correctAbstentions()).isEqualTo(6);
        assertThat(evaluation.elapsed()).isLessThan(LATENCY_GUARD);
    }

    private Evaluation evaluate(RegistrySnapshot snapshot, Dataset dataset) {
        Map<String, McpServerEntity> servers = new LinkedHashMap<>();
        snapshot.servers().forEach(label -> servers.put(label.name(), server(label, snapshot.recordedAt())));

        List<QueryResult> results = new ArrayList<>();
        int totalRelevant = 0;
        int retrievedRelevant = 0;
        double ndcgSum = 0.0;
        int labelledQueries = 0;
        int noMatchQueries = 0;
        int acceptabilityQueries = 0;
        int topThreeAcceptable = 0;
        int forbiddenViolations = 0;
        int expectedAbstentions = 0;
        int correctAbstentions = 0;
        long started = System.nanoTime();

        for (QueryLabel query : dataset.queries()) {
            RequirementAnalysis analysis = analyzer.analyze(query.requirement());
            List<McpServerEntity> retrieved = retrieve(servers.values().stream().toList(), analysis);
            List<RankedLabel> ranked = retrieved.stream()
                    .map(candidate -> new RankedLabel(
                            candidate.getRegistryName(), rankingService.rank(candidate, analysis).score()))
                    .filter(result -> result.score() > 0.0)
                    .sorted(Comparator.comparingDouble(RankedLabel::score).reversed()
                            .thenComparing(RankedLabel::serverName))
                    .toList();

            int queryRetrievedRelevant = (int) retrieved.stream()
                    .map(McpServerEntity::getRegistryName)
                    .filter(query.relevance()::containsKey)
                    .count();
            int queryTotalRelevant = query.relevance().size();
            int acceptableRank = firstRank(ranked, query.acceptableServerIds());
            List<String> forbiddenInTopThree = ranked.stream()
                    .limit(ACCEPTABILITY_CUTOFF)
                    .map(RankedLabel::serverName)
                    .filter(query.forbiddenServerIds()::contains)
                    .toList();
            boolean predictedAbstain = ranked.isEmpty() || ranked.getFirst().score() < dataset.strongMatchThreshold();

            totalRelevant += queryTotalRelevant;
            retrievedRelevant += queryRetrievedRelevant;
            forbiddenViolations += forbiddenInTopThree.size();
            if (query.relevance().isEmpty()) {
                noMatchQueries++;
            } else {
                labelledQueries++;
                ndcgSum += ndcg(ranked, query.relevance(), RANKING_CUTOFF);
            }
            if (!query.acceptableServerIds().isEmpty()) {
                acceptabilityQueries++;
                if (acceptableRank > 0 && acceptableRank <= ACCEPTABILITY_CUTOFF) {
                    topThreeAcceptable++;
                }
            }
            if (query.expectedAbstain()) {
                expectedAbstentions++;
                if (predictedAbstain) {
                    correctAbstentions++;
                }
            }

            results.add(new QueryResult(
                    query.id(),
                    ranked,
                    queryRetrievedRelevant,
                    queryTotalRelevant,
                    acceptableRank,
                    predictedAbstain,
                    query.expectedAbstain(),
                    forbiddenInTopThree
            ));
        }

        return new Evaluation(
                labelledQueries,
                noMatchQueries,
                (double) retrievedRelevant / totalRelevant,
                ndcgSum / labelledQueries,
                acceptabilityQueries,
                topThreeAcceptable,
                forbiddenViolations,
                expectedAbstentions,
                correctAbstentions,
                Duration.ofNanos(System.nanoTime() - started),
                List.copyOf(results)
        );
    }

    private static List<McpServerEntity> retrieve(List<McpServerEntity> servers, RequirementAnalysis analysis) {
        return servers.stream()
                .filter(server -> !"deleted".equalsIgnoreCase(server.getStatus()))
                .filter(server -> analysis.keywords().stream().anyMatch(keyword -> contains(server, keyword)))
                .limit(RETRIEVAL_LIMIT)
                .toList();
    }

    private static boolean contains(McpServerEntity server, String keyword) {
        return lower(server.getRegistryName()).contains(keyword)
                || lower(server.getTitle()).contains(keyword)
                || lower(server.getDescription()).contains(keyword);
    }

    private static double ndcg(List<RankedLabel> ranked, Map<String, Integer> relevance, int cutoff) {
        double dcg = 0.0;
        for (int index = 0; index < Math.min(cutoff, ranked.size()); index++) {
            int grade = relevance.getOrDefault(ranked.get(index).serverName(), 0);
            dcg += gain(grade) / log2(index + 2.0);
        }
        List<Integer> idealGrades = relevance.values().stream().sorted(Comparator.reverseOrder()).toList();
        double idealDcg = 0.0;
        for (int index = 0; index < Math.min(cutoff, idealGrades.size()); index++) {
            idealDcg += gain(idealGrades.get(index)) / log2(index + 2.0);
        }
        return idealDcg == 0.0 ? 0.0 : dcg / idealDcg;
    }

    private static double gain(int grade) {
        return Math.pow(2.0, grade) - 1.0;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }

    private static double rounded(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static int firstRank(List<RankedLabel> ranked, List<String> acceptableServerIds) {
        for (int index = 0; index < ranked.size(); index++) {
            if (acceptableServerIds.contains(ranked.get(index).serverName())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static McpServerEntity server(ServerLabel label, Instant recordedAt) {
        McpServerEntity entity = McpServerEntity.create(label.name(), recordedAt);
        entity.updateFrom(new RegistryClient.RegistryServerPayload(
                label.name(),
                label.title(),
                label.description(),
                label.version(),
                label.status(),
                "{}",
                true,
                label.repositoryUrl(),
                label.packageCount(),
                label.remoteCount(),
                List.of(),
                List.of()
        ), recordedAt);
        return entity;
    }

    private <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream input = RegistryRelevanceEvaluationTest.class.getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return objectMapper.readValue(input, type);
        }
    }

    private record RegistrySnapshot(
            String schemaVersion,
            String snapshotId,
            Instant recordedAt,
            String source,
            String recording,
            List<String> sourceRequests,
            List<ServerLabel> servers
    ) {
    }

    private record ServerLabel(
            String name,
            String title,
            String description,
            String version,
            String status,
            Instant updatedAt,
            String repositoryUrl,
            int packageCount,
            int remoteCount
    ) {
    }

    private record Dataset(
            String schemaVersion,
            String datasetId,
            String snapshotId,
            double strongMatchThreshold,
            List<QueryLabel> queries
    ) {
    }

    private record QueryLabel(
            String id,
            String cohort,
            String requirement,
            List<String> requiredCapabilities,
            List<String> forbiddenCapabilities,
            List<String> constraints,
            Map<String, Integer> relevance,
            List<String> acceptableServerIds,
            List<String> forbiddenServerIds,
            boolean expectedAbstain,
            String notes
    ) {
    }

    private record RankedLabel(String serverName, double score) {
    }

    private record QueryResult(
            String queryId,
            List<RankedLabel> ranked,
            int retrievedRelevant,
            int totalRelevant,
            int acceptableRank,
            boolean predictedAbstain,
            boolean expectedAbstain,
            List<String> forbiddenInTopThree
    ) {
    }

    private record Evaluation(
            int labelledQueries,
            int noMatchQueries,
            double recallAt100,
            double ndcgAt10,
            int acceptabilityQueries,
            int topThreeAcceptable,
            int forbiddenViolations,
            int expectedAbstentions,
            int correctAbstentions,
            Duration elapsed,
            List<QueryResult> results
    ) {
        String summary() {
            return "labelled=%d, noMatch=%d, Recall@100=%.4f, NDCG@10=%.4f, top3=%d/%d, "
                    .formatted(
                            labelledQueries,
                            noMatchQueries,
                            recallAt100,
                            ndcgAt10,
                            topThreeAcceptable,
                            acceptabilityQueries
                    )
                    + "forbiddenViolations=%d, abstention=%d/%d, elapsed=%s"
                    .formatted(forbiddenViolations, correctAbstentions, expectedAbstentions, elapsed);
        }
    }
}
