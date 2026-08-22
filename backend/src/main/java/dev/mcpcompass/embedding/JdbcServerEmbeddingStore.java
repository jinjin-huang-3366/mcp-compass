package dev.mcpcompass.embedding;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class JdbcServerEmbeddingStore implements ServerEmbeddingStore {
    private final NamedParameterJdbcTemplate jdbc;

    JdbcServerEmbeddingStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void updateByRegistryName(List<IndexedServerEmbedding> embeddings) {
        if (embeddings.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = embeddings.stream()
                .map(embedding -> new MapSqlParameterSource()
                        .addValue("registryName", embedding.registryName())
                        .addValue("model", embedding.embedding().model())
                        .addValue("embedding", embedding.embedding().postgresLiteral()))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                UPDATE mcp_server
                SET search_embedding = CAST(:embedding AS vector),
                    search_embedding_model = :model
                WHERE registry_name = :registryName
                """, batch);
    }

    @Override
    public List<NearestServer> findNearestServers(
            EmbeddingVector queryEmbedding,
            int limit,
            double minSimilarity
    ) {
        return jdbc.query("""
                SELECT id,
                       1 - (search_embedding <=> CAST(:embedding AS vector)) AS similarity
                FROM mcp_server
                WHERE search_embedding IS NOT NULL
                  AND search_embedding_model = :model
                  AND (status IS NULL OR lower(status) <> 'deleted')
                  AND 1 - (search_embedding <=> CAST(:embedding AS vector)) >= :minSimilarity
                ORDER BY search_embedding <=> CAST(:embedding AS vector)
                LIMIT :limit
                """, new MapSqlParameterSource()
                .addValue("model", queryEmbedding.model())
                .addValue("embedding", queryEmbedding.postgresLiteral())
                .addValue("minSimilarity", minSimilarity)
                .addValue("limit", limit),
                (resultSet, rowNumber) -> new NearestServer(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getDouble("similarity")
                ));
    }
}
