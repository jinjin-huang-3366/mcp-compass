package dev.mcpcompass.search;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
class JdbcSearchDocumentStore implements SearchDocumentStore {
    static final int DOCUMENT_VERSION = 1;
    private static final int MAX_ARTIFACT_CONTENT = 32_768;
    private static final String DOCUMENTS = """
            SELECT server.id, server.registry_name,
                   concat_ws(E'\\n',
                       'service: ' || coalesce(server.registry_name, ''),
                       'title: ' || coalesce(server.title, ''),
                       'description: ' || coalesce(server.description, ''),
                       'repository: ' || coalesce(server.repository_url, ''),
                       coalesce(tools.content, ''),
                       coalesce(capabilities.content, ''),
                       coalesce(repository_tools.content, ''),
                       coalesce(repository_artifacts.content, '')
                   ) AS content
            FROM mcp_server server
            LEFT JOIN LATERAL (
                SELECT string_agg('tool: ' || tool.name || ' ' || coalesce(tool.description, ''), E'\\n' ORDER BY tool.name) AS content
                FROM mcp_tool tool WHERE tool.server_id = server.id
            ) tools ON true
            LEFT JOIN LATERAL (
                SELECT string_agg('capability: ' || capability.canonical_name, E'\\n' ORDER BY capability.canonical_name) AS content
                FROM mcp_server_capability server_capability
                JOIN capability ON capability.id = server_capability.capability_id
                WHERE server_capability.server_id = server.id
            ) capabilities ON true
            LEFT JOIN LATERAL (
                SELECT string_agg('repository tool: ' || tool.name || ' ' || coalesce(tool.description, ''), E'\\n' ORDER BY artifact.source_url, tool.ordinal) AS content
                FROM repository_enrichment_artifact artifact
                JOIN repository_enrichment_tool tool ON tool.artifact_id = artifact.id
                WHERE artifact.server_id = server.id
            ) repository_tools ON true
            LEFT JOIN LATERAL (
                SELECT string_agg('repository metadata: ' || left(artifact.content, %d), E'\\n' ORDER BY artifact.source_url) AS content
                FROM repository_enrichment_artifact artifact
                WHERE artifact.server_id = server.id
            ) repository_artifacts ON true
            """.formatted(MAX_ARTIFACT_CONTENT);

    private final NamedParameterJdbcTemplate jdbc;

    JdbcSearchDocumentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchDocument> buildForRegistryNames(Collection<String> registryNames) {
        if (registryNames == null || registryNames.isEmpty()) {
            return List.of();
        }
        return query(DOCUMENTS + " WHERE server.registry_name IN (:registryNames) ORDER BY server.registry_name",
                new MapSqlParameterSource("registryNames", registryNames));
    }

    @Override
    public List<SearchDocument> buildAll() {
        return query(DOCUMENTS + " ORDER BY server.registry_name", new MapSqlParameterSource());
    }

    @Override
    public void replace(List<SearchDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = documents.stream().map(document -> new MapSqlParameterSource()
                .addValue("serverId", document.serverId())
                .addValue("version", document.version())
                .addValue("content", document.content())
                .addValue("sha256", sha256(document.content()))
                .addValue("builtAt", Timestamp.from(Instant.now())))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("""
                INSERT INTO mcp_server_search_document (server_id, document_version, content, content_sha256, built_at)
                VALUES (:serverId, :version, :content, :sha256, :builtAt)
                ON CONFLICT (server_id) DO UPDATE SET
                    document_version = EXCLUDED.document_version,
                    content = EXCLUDED.content,
                    content_sha256 = EXCLUDED.content_sha256,
                    built_at = EXCLUDED.built_at
                WHERE mcp_server_search_document.document_version <> EXCLUDED.document_version
                   OR mcp_server_search_document.content_sha256 <> EXCLUDED.content_sha256
                """, batch);
    }

    private List<SearchDocument> query(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql, parameters, (resultSet, rowNumber) -> new SearchDocument(
                resultSet.getObject("id", UUID.class), resultSet.getString("registry_name"),
                DOCUMENT_VERSION, resultSet.getString("content")
        ));
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte valueByte : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", valueByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
