package dev.mcpcompass.github;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
class GithubRepositoryEnrichmentStore {
    private final JdbcTemplate jdbcTemplate;

    GithubRepositoryEnrichmentStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    void replace(String registryName, List<GithubRepositoryArtifact> artifacts, Instant fetchedAt) {
        UUID serverId = jdbcTemplate.query(
                "SELECT id FROM mcp_server WHERE registry_name = ?",
                (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
                registryName
        ).stream().findFirst().orElse(null);
        if (serverId == null) {
            return;
        }

        jdbcTemplate.update("DELETE FROM repository_enrichment_artifact WHERE server_id = ?", serverId);
        for (GithubRepositoryArtifact artifact : artifacts) {
            UUID artifactId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO repository_enrichment_artifact (
                        id, server_id, artifact_type, source_path, source_url, source_revision,
                        media_type, content, content_sha256, fetched_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifactId, serverId, artifact.kind().name(), artifact.sourcePath(), artifact.sourceUrl(),
                    artifact.sourceRevision(), artifact.mediaType(), artifact.content(), artifact.contentSha256(), fetchedAt
            );
            int ordinal = 0;
            for (GithubRepositoryArtifact.StaticTool tool : artifact.tools()) {
                jdbcTemplate.update("""
                        INSERT INTO repository_enrichment_tool (
                            id, artifact_id, ordinal, name, description, input_schema
                        ) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
                        """,
                        UUID.randomUUID(), artifactId, ordinal++, tool.name(), tool.description(), tool.inputSchema()
                );
            }
        }
    }
}
