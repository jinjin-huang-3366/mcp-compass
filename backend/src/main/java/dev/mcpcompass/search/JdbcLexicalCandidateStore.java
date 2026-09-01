package dev.mcpcompass.search;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class JdbcLexicalCandidateStore implements LexicalCandidateStore {
    private static final int MAX_KEYWORD_LENGTH = 128;
    private static final String FIND_CANDIDATES = """
            WITH search_terms AS (
                SELECT DISTINCT LOWER(BTRIM(input.term)) AS term
                FROM UNNEST(CAST(? AS TEXT[])) AS input(term)
                WHERE BTRIM(input.term) <> ''
            ),
            document_matches AS (
                SELECT server.id AS server_id,
                       3.0 * TS_RANK_CD(
                           TO_TSVECTOR(
                               'simple',
                               document.content
                           ),
                           PLAINTO_TSQUERY('simple', search_terms.term)
                       )
                       + GREATEST(
                           SIMILARITY(search_terms.term, LOWER(document.content)),
                           STRICT_WORD_SIMILARITY(
                               search_terms.term,
                               LOWER(document.content)
                           )
                       ) AS match_score
                FROM mcp_server server
                JOIN mcp_server_search_document document ON document.server_id = server.id
                CROSS JOIN search_terms
                WHERE TO_TSVECTOR(
                          'simple',
                          document.content
                      ) @@ PLAINTO_TSQUERY('simple', search_terms.term)
                   OR STRICT_WORD_SIMILARITY(
                          search_terms.term,
                          LOWER(document.content)
                      ) >= 0.3
            ),
            server_fallback_matches AS (
                SELECT server.id AS server_id,
                       3.0 * TS_RANK_CD(TO_TSVECTOR('simple', COALESCE(server.registry_name, '') || ' ' || COALESCE(server.title, '') || ' ' || COALESCE(server.description, '')), PLAINTO_TSQUERY('simple', search_terms.term))
                       + STRICT_WORD_SIMILARITY(search_terms.term, LOWER(COALESCE(server.registry_name, '') || ' ' || COALESCE(server.title, '') || ' ' || COALESCE(server.description, ''))) AS match_score
                FROM mcp_server server
                CROSS JOIN search_terms
                WHERE NOT EXISTS (SELECT 1 FROM mcp_server_search_document document WHERE document.server_id = server.id)
                  AND (TO_TSVECTOR('simple', COALESCE(server.registry_name, '') || ' ' || COALESCE(server.title, '') || ' ' || COALESCE(server.description, '')) @@ PLAINTO_TSQUERY('simple', search_terms.term)
                       OR STRICT_WORD_SIMILARITY(search_terms.term, LOWER(COALESCE(server.registry_name, '') || ' ' || COALESCE(server.title, '') || ' ' || COALESCE(server.description, ''))) >= 0.3)
            ),
            tool_matches AS (
                SELECT tool.server_id,
                       2.5 * TS_RANK_CD(
                           TO_TSVECTOR(
                               'simple',
                               COALESCE(tool.name, '') || ' ' || COALESCE(tool.description, '')
                           ),
                           PLAINTO_TSQUERY('simple', search_terms.term)
                       )
                       + STRICT_WORD_SIMILARITY(
                           search_terms.term,
                           LOWER(COALESCE(tool.name, '') || ' ' || COALESCE(tool.description, ''))
                       ) AS match_score
                FROM mcp_tool tool
                CROSS JOIN search_terms
                WHERE TO_TSVECTOR(
                          'simple',
                          COALESCE(tool.name, '') || ' ' || COALESCE(tool.description, '')
                      ) @@ PLAINTO_TSQUERY('simple', search_terms.term)
                   OR STRICT_WORD_SIMILARITY(
                          search_terms.term,
                          LOWER(COALESCE(tool.name, '') || ' ' || COALESCE(tool.description, ''))
                      ) >= 0.3
            ),
            capability_matches AS (
                SELECT server_capability.server_id,
                       3.5 * TS_RANK_CD(
                           TO_TSVECTOR('simple', capability.canonical_name),
                           PLAINTO_TSQUERY('simple', search_terms.term)
                       )
                       + STRICT_WORD_SIMILARITY(
                           search_terms.term,
                           LOWER(capability.canonical_name)
                       ) AS match_score
                FROM mcp_server_capability server_capability
                JOIN capability ON capability.id = server_capability.capability_id
                CROSS JOIN search_terms
                WHERE TO_TSVECTOR('simple', capability.canonical_name)
                          @@ PLAINTO_TSQUERY('simple', search_terms.term)
                   OR STRICT_WORD_SIMILARITY(
                          search_terms.term,
                          LOWER(capability.canonical_name)
                      ) >= 0.3
            ),
            scored AS (
                SELECT matches.server_id, SUM(matches.match_score) AS lexical_score
                FROM (
                    SELECT * FROM document_matches
                    UNION ALL
                    SELECT * FROM server_fallback_matches
                    UNION ALL
                    SELECT * FROM tool_matches
                    UNION ALL
                    SELECT * FROM capability_matches
                ) matches
                GROUP BY matches.server_id
            )
            SELECT server.id AS server_id, scored.lexical_score
            FROM scored
            JOIN mcp_server server ON server.id = scored.server_id
            WHERE server.status IS NULL OR LOWER(server.status) <> 'deleted'
            ORDER BY scored.lexical_score DESC, LOWER(server.registry_name), server.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public JdbcLexicalCandidateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LexicalCandidate> findCandidates(List<String> keywords, int limit) {
        Objects.requireNonNull(keywords, "keywords must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }

        List<String> normalizedKeywords = normalizedKeywords(keywords);
        if (normalizedKeywords.isEmpty()) {
            return List.of();
        }

        return jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(FIND_CANDIDATES);
            Array keywordArray = connection.createArrayOf("text", normalizedKeywords.toArray(String[]::new));
            statement.setArray(1, keywordArray);
            statement.setInt(2, limit);
            return statement;
        }, (resultSet, rowNumber) -> new LexicalCandidate(
                resultSet.getObject("server_id", java.util.UUID.class),
                resultSet.getDouble("lexical_score")
        ));
    }

    private static List<String> normalizedKeywords(List<String> keywords) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        keywords.stream()
                .filter(Objects::nonNull)
                .map(keyword -> keyword.strip().toLowerCase(Locale.ROOT))
                .filter(keyword -> !keyword.isBlank())
                .map(keyword -> keyword.substring(0, Math.min(keyword.length(), MAX_KEYWORD_LENGTH)))
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }
}
