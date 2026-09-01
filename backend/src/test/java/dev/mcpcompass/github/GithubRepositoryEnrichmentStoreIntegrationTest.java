package dev.mcpcompass.github;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class GithubRepositoryEnrichmentStoreIntegrationTest {
    private static final UUID SERVER_ID = UUID.fromString("cb8732d0-bc93-4989-aa39-ca4058f0e31e");
    private static final Instant FETCHED_AT = Instant.parse("2026-08-31T12:00:00Z");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer DATABASE =
            new PostgreSQLContainer("pgvector/pgvector:0.8.6-pg18-trixie");

    private final JdbcTemplate jdbcTemplate;
    private final GithubRepositoryEnrichmentStore store;

    @Autowired
    GithubRepositoryEnrichmentStoreIntegrationTest(
            JdbcTemplate jdbcTemplate,
            GithubRepositoryEnrichmentStore store
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.store = store;
    }

    @BeforeEach
    void insertServer() {
        jdbcTemplate.update("""
                INSERT INTO mcp_server (id, registry_name, first_seen_at, last_seen_at)
                VALUES (?, 'io.example/server', ?, ?)
                ON CONFLICT (registry_name) DO NOTHING
                """, SERVER_ID, FETCHED_AT, FETCHED_AT);
    }

    @Test
    void repeatedRefreshPersistsOneFreshArtifactAndOneProvenancedTool() {
        GithubRepositoryArtifact artifact = new GithubRepositoryArtifact(
                GithubRepositoryArtifact.Kind.STATIC_TOOL_METADATA,
                ".mcp/server.json",
                "https://api.github.com/repos/example/server/contents/.mcp/server.json",
                "2222222222222222222222222222222222222222",
                "application/json",
                "{\"tools\":[]}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                List.of(new GithubRepositoryArtifact.StaticTool(
                        "list_issues", "List repository issues", "{\"type\":\"object\"}"))
        );

        store.replace("io.example/server", List.of(artifact), FETCHED_AT);
        store.replace("io.example/server", List.of(artifact), FETCHED_AT);

        assertThat(count("repository_enrichment_artifact")).isEqualTo(1);
        assertThat(count("repository_enrichment_tool")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT artifact_type, source_revision, content_sha256, fetched_at
                FROM repository_enrichment_artifact WHERE server_id = ?
                """, SERVER_ID))
                .containsEntry("artifact_type", "STATIC_TOOL_METADATA")
                .containsEntry("source_revision", "2222222222222222222222222222222222222222")
                .containsEntry("content_sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT name FROM repository_enrichment_tool tool
                JOIN repository_enrichment_artifact artifact ON artifact.id = tool.artifact_id
                WHERE artifact.server_id = ?
                """, String.class, SERVER_ID)).isEqualTo("list_issues");
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
