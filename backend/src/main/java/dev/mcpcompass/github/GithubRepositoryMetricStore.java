package dev.mcpcompass.github;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class GithubRepositoryMetricStore {
    private final JdbcTemplate jdbcTemplate;

    GithubRepositoryMetricStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void upsert(String registryName, String repositoryUrl, GithubRepositoryMetadata metadata, Instant collectedAt) {
        jdbcTemplate.update("""
                INSERT INTO repo_metrics (
                    server_id, repository_url, archived, license, last_commit_at, last_release_at, collected_at
                )
                SELECT id, ?, ?, ?, ?, ?, ? FROM mcp_server WHERE registry_name = ?
                ON CONFLICT (server_id) DO UPDATE SET
                    repository_url = EXCLUDED.repository_url,
                    archived = EXCLUDED.archived,
                    license = EXCLUDED.license,
                    last_commit_at = EXCLUDED.last_commit_at,
                    last_release_at = EXCLUDED.last_release_at,
                    collected_at = EXCLUDED.collected_at
                """,
                repositoryUrl,
                metadata.archived(),
                metadata.licenseSpdx(),
                metadata.lastActivityAt(),
                metadata.latestReleaseAt(),
                collectedAt,
                registryName
        );
    }
}
