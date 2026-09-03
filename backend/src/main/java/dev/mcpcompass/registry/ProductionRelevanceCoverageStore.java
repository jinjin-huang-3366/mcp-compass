package dev.mcpcompass.registry;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class ProductionRelevanceCoverageStore {
    private final JdbcTemplate jdbc;

    ProductionRelevanceCoverageStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Coverage snapshot() {
        int corpus = count("SELECT count(*) FROM mcp_server");
        return new Coverage(
                corpus,
                count("SELECT count(DISTINCT server_id) FROM mcp_server_capability"),
                count("SELECT count(*) FROM capability"),
                count("SELECT count(*) FROM mcp_server_search_document"),
                count("SELECT count(*) FROM mcp_server WHERE search_embedding IS NOT NULL"),
                count("SELECT count(*) FROM repo_metrics"),
                count("SELECT count(DISTINCT server_id) FROM repository_enrichment_artifact")
        );
    }

    private int count(String sql) {
        Integer result = jdbc.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }

    record Coverage(
            int corpusServers,
            int serversWithCapabilities,
            int normalizedCapabilities,
            int serversWithSearchDocuments,
            int serversWithEmbeddings,
            int serversWithGithubMetrics,
            int serversWithGithubArtifacts
    ) {
    }
}
