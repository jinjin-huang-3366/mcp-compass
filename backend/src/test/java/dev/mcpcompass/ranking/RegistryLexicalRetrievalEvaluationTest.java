package dev.mcpcompass.ranking;

import dev.mcpcompass.requirement.HeuristicRequirementAnalyzer;
import dev.mcpcompass.search.JdbcLexicalCandidateStore;
import dev.mcpcompass.search.LexicalCandidateStore;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RegistryLexicalRetrievalEvaluationTest {
    private static final String SNAPSHOT = "/fixtures/ranking/registry-snapshot-2026-08-31.json";
    private static final String LABELS = "/fixtures/ranking/registry-relevance-v1.json";
    private static final int RETRIEVAL_LIMIT = 100;

    @Container
    private static final PostgreSQLContainer DATABASE =
            new PostgreSQLContainer("pgvector/pgvector:0.8.6-pg18-trixie");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HeuristicRequirementAnalyzer analyzer = new HeuristicRequirementAnalyzer();

    @Test
    void rel01RecallAt100MeetsTheRel03GateWithProductionSql() throws IOException {
        RegistrySnapshot snapshot = read(SNAPSHOT, RegistrySnapshot.class);
        Dataset dataset = read(LABELS, Dataset.class);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()
        );
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<UUID, String> serverNames = persistSnapshot(jdbc, snapshot);
        JdbcLexicalCandidateStore store = new JdbcLexicalCandidateStore(jdbc);

        int totalRelevant = dataset.queries().stream().mapToInt(query -> query.relevance().size()).sum();
        int retrievedRelevant = 0;
        long started = System.nanoTime();
        for (QueryLabel query : dataset.queries()) {
            Set<String> retrievedNames = store.findCandidates(
                            analyzer.analyze(query.requirement()).keywords(),
                            RETRIEVAL_LIMIT
                    ).stream()
                    .map(LexicalCandidateStore.LexicalCandidate::serverId)
                    .map(serverNames::get)
                    .collect(java.util.stream.Collectors.toSet());
            retrievedRelevant += (int) query.relevance().keySet().stream()
                    .filter(retrievedNames::contains)
                    .count();
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        double recallAt100 = (double) retrievedRelevant / totalRelevant;

        System.out.printf(
                "REL-03 Registry lexical retrieval: Recall@100=%d/%d (%.1f%%), elapsed=%s%n",
                retrievedRelevant,
                totalRelevant,
                recallAt100 * 100.0,
                elapsed
        );
        assertThat(totalRelevant).isEqualTo(53);
        assertThat(retrievedRelevant).isEqualTo(53);
        assertThat(recallAt100).isGreaterThanOrEqualTo(0.95);
    }

    private static Map<UUID, String> persistSnapshot(JdbcTemplate jdbc, RegistrySnapshot snapshot) {
        Map<UUID, String> namesById = new HashMap<>();
        snapshot.servers().forEach(server -> {
            UUID id = UUID.nameUUIDFromBytes(
                    server.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );
            jdbc.update("""
                    INSERT INTO mcp_server (
                        id, registry_name, title, description, version, status,
                        official_registry_provenance, repository_url, package_count, remote_count,
                        first_seen_at, last_seen_at
                    ) VALUES (?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?)
                    """,
                    id,
                    server.name(),
                    server.title(),
                    server.description(),
                    server.version(),
                    server.status(),
                    server.repositoryUrl(),
                    server.packageCount(),
                    server.remoteCount(),
                    Timestamp.from(snapshot.recordedAt()),
                    Timestamp.from(server.updatedAt())
            );
            namesById.put(id, server.name());
        });
        return Map.copyOf(namesById);
    }

    private <T> T read(String resource, Class<T> type) throws IOException {
        try (InputStream input = RegistryLexicalRetrievalEvaluationTest.class.getResourceAsStream(resource)) {
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
}
