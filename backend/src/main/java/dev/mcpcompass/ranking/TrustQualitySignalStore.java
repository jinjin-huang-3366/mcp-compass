package dev.mcpcompass.ranking;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static java.util.stream.Collectors.toMap;

@Component
public class TrustQualitySignalStore {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TrustQualitySignalStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<UUID, TrustQualitySignals> findByServerIds(Collection<UUID> serverIds) {
        if (serverIds.isEmpty()) {
            return Map.of();
        }
        return jdbcTemplate.query("""
                SELECT server_id, archived, license, last_commit_at, last_release_at, collected_at
                FROM repo_metrics
                WHERE server_id IN (:serverIds)
                """,
                new MapSqlParameterSource("serverIds", serverIds),
                (resultSet, rowNumber) -> {
                    Instant lastCommitAt = resultSet.getTimestamp("last_commit_at") == null
                            ? null : resultSet.getTimestamp("last_commit_at").toInstant();
                    Instant lastReleaseAt = resultSet.getTimestamp("last_release_at") == null
                            ? null : resultSet.getTimestamp("last_release_at").toInstant();
                    return Map.entry(
                            resultSet.getObject("server_id", UUID.class),
                            new TrustQualitySignals(
                                    resultSet.getObject("archived", Boolean.class),
                                    resultSet.getString("license"),
                                    latest(lastCommitAt, lastReleaseAt),
                                    resultSet.getTimestamp("collected_at").toInstant()
                            )
                    );
                }
        ).stream().collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Instant latest(Instant first, Instant second) {
        if (first == null) {
            return second;
        }
        return second != null && second.isAfter(first) ? second : first;
    }
}
