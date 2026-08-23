package dev.mcpcompass.github;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GithubRepositoryMetricStoreTest {
    @Test
    void upsertsAllEnrichmentFieldsForThePersistedRegistryServer() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GithubRepositoryMetricStore store = new GithubRepositoryMetricStore(jdbcTemplate);
        Instant activity = Instant.parse("2026-08-20T10:00:00Z");
        Instant release = Instant.parse("2026-07-01T12:00:00Z");
        Instant collected = Instant.parse("2026-08-23T10:00:00Z");

        store.upsert(
                "io.example/server",
                "https://github.com/example/server",
                new GithubRepositoryMetadata(activity, release, true, "Apache-2.0"),
                collected
        );

        verify(jdbcTemplate).update(
                anyString(),
                eq("https://github.com/example/server"),
                eq(true),
                eq("Apache-2.0"),
                eq(activity),
                eq(release),
                eq(collected),
                eq("io.example/server")
        );
    }
}
