package dev.mcpcompass.search;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcLexicalCandidateStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

    @Container
    private static final PostgreSQLContainer DATABASE =
            new PostgreSQLContainer("pgvector/pgvector:0.8.6-pg18-trixie");

    private static JdbcTemplate jdbc;
    private static JdbcLexicalCandidateStore store;
    private static JdbcSearchDocumentStore documentStore;

    @BeforeAll
    static void migrate() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()
        );
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcLexicalCandidateStore(jdbc);
        documentStore = new JdbcSearchDocumentStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @BeforeEach
    void clearDatabase() {
        jdbc.execute("TRUNCATE TABLE capability, mcp_server CASCADE");
    }

    @Test
    void retrievesAcrossServerToolAndCapabilityTextIncludingTrigramVariants() {
        UUID serverTextId = server("dev.example/server-text", "Webhook relay", "Routes webhook events", "active");
        UUID toolTextId = server("dev.example/tool-text", "Messaging", "Communication utilities", "active");
        tool(toolTextId, "send_sms", "Send transactional messages");
        UUID capabilityTextId = server("dev.example/capability-text", "Database", "Data access", "active");
        capability(capabilityTextId, "database.postgres.read");

        List<LexicalCandidateStore.LexicalCandidate> candidates = store.findCandidates(
                List.of("webhook", "sms", "postgre"),
                100
        );

        assertThat(candidates).extracting(LexicalCandidateStore.LexicalCandidate::serverId)
                .contains(serverTextId, toolTextId, capabilityTextId);
        assertThat(candidates).allMatch(candidate -> candidate.score() > 0.0);
    }

    @Test
    void appliesStableScoreNameAndIdOrderingBeforeTheLimitAndExcludesDeletedServers() {
        UUID secondByName = server("dev.example/b", "Generic", "No lexical evidence", "active");
        UUID firstByName = server("dev.example/a", "Generic", "No lexical evidence", "active");
        UUID deleted = server("dev.example/0-deleted", "Generic", "No lexical evidence", "deleted");
        tool(secondByName, "unique_operation", "Perform the unique operation");
        tool(firstByName, "unique_operation", "Perform the unique operation");
        tool(deleted, "unique_operation", "Perform the unique operation");

        List<LexicalCandidateStore.LexicalCandidate> candidates = store.findCandidates(List.of("unique"), 1);

        assertThat(candidates).extracting(LexicalCandidateStore.LexicalCandidate::serverId)
                .containsExactly(firstByName);
    }

    @Test
    void normalizesAndDeduplicatesKeywordsBeforeQuerying() {
        UUID serverId = server("dev.example/github", "GitHub", "Repository tools", "active");

        List<LexicalCandidateStore.LexicalCandidate> candidates = store.findCandidates(
                List.of(" GitHub ", "github", "", "   "),
                100
        );

        assertThat(candidates).extracting(LexicalCandidateStore.LexicalCandidate::serverId)
                .containsExactly(serverId);
        assertThat(store.findCandidates(List.of("", "  "), 100)).isEmpty();
    }

    @Test
    void buildsVersionedDocumentsFromCatalogToolsCapabilitiesAndRepositoryEnrichment() {
        UUID serverId = server("dev.example/calendar", "Calendar", "Scheduling integration", "active");
        tool(serverId, "list_events", "List calendar events");
        capability(serverId, "calendar.events.read");
        UUID artifactId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO repository_enrichment_artifact
                    (id, server_id, artifact_type, source_url, media_type, content, content_sha256, fetched_at)
                VALUES (?, ?, 'README', 'https://github.com/example/calendar/README.md', 'text/markdown',
                        'Calendaring for team availability', repeat('a', 64), ?)
                """, artifactId, serverId, Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO repository_enrichment_tool (id, artifact_id, ordinal, name, description)
                VALUES (?, ?, 0, 'find_free_time', 'Find calendar availability')
                """, UUID.randomUUID(), artifactId);

        List<SearchDocument> documents = documentStore.buildForRegistryNames(List.of("dev.example/calendar"));
        documentStore.replace(documents);

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.version()).isEqualTo(1);
            assertThat(document.content()).contains("service: dev.example/calendar", "tool: list_events",
                    "capability: calendar.events.read", "repository tool: find_free_time",
                    "Calendaring for team availability");
        });
        assertThat(store.findCandidates(List.of("availability"), 100))
                .extracting(LexicalCandidateStore.LexicalCandidate::serverId)
                .containsExactly(serverId);
    }

    private static UUID server(String registryName, String title, String description, String status) {
        UUID id = UUID.nameUUIDFromBytes(registryName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("""
                INSERT INTO mcp_server (
                    id, registry_name, title, description, version, status, first_seen_at, last_seen_at
                ) VALUES (?, ?, ?, ?, '1.0.0', ?, ?, ?)
                """, id, registryName, title, description, status, Timestamp.from(NOW), Timestamp.from(NOW));
        return id;
    }

    private static void tool(UUID serverId, String name, String description) {
        UUID id = UUID.nameUUIDFromBytes(
                (serverId + "\0" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        jdbc.update("""
                INSERT INTO mcp_tool (id, server_id, name, description)
                VALUES (?, ?, ?, ?)
                """, id, serverId, name, description);
    }

    private static void capability(UUID serverId, String canonicalName) {
        UUID id = UUID.nameUUIDFromBytes(canonicalName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO capability (id, canonical_name) VALUES (?, ?)", id, canonicalName);
        jdbc.update("""
                INSERT INTO mcp_server_capability (server_id, capability_id, confidence, source)
                VALUES (?, ?, 1.0, 'test')
                """, serverId, id);
    }
}
