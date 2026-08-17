package dev.mcpcompass.capability;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcCapabilityMetadataStoreTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final JdbcCapabilityMetadataStore store = new JdbcCapabilityMetadataStore(jdbc);

    @Test
    void replacesServerToolAndCapabilityMappings() {
        UUID serverId = UUID.fromString("8f0904fe-44fd-47f6-a29c-089a534780b1");
        UUID capabilityId = UUID.fromString("c4341ddd-dd7f-4769-a43e-487bd77f046c");
        NormalizedCapabilityMetadata.NormalizedCapability capability =
                new NormalizedCapabilityMetadata.NormalizedCapability(
                        "github.issue.read",
                        "Read issues",
                        1.0,
                        "tool-metadata"
                );
        NormalizedCapabilityMetadata metadata = new NormalizedCapabilityMetadata(
                List.of(new NormalizedCapabilityMetadata.NormalizedTool(
                        "get_issue",
                        "Get an issue",
                        "{\"type\":\"object\"}",
                        List.of(capability)
                )),
                List.of(capability)
        );
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class)))
                .thenReturn(capabilityId);

        store.replaceForServer(serverId, metadata);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(6)).update(sql.capture(), any(SqlParameterSource.class));
        assertThat(sql.getAllValues().stream().map(JdbcCapabilityMetadataStoreTest::singleLine))
                .anyMatch(value -> value.startsWith("DELETE FROM mcp_tool_capability"))
                .anyMatch(value -> value.equals("DELETE FROM mcp_tool WHERE server_id = :serverId"))
                .anyMatch(value -> value.equals("DELETE FROM mcp_server_capability WHERE server_id = :serverId"))
                .anyMatch(value -> value.startsWith("INSERT INTO mcp_server_capability"))
                .anyMatch(value -> value.startsWith("INSERT INTO mcp_tool"))
                .anyMatch(value -> value.startsWith("INSERT INTO mcp_tool_capability"));
        verify(jdbc).queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class));
    }

    @Test
    void clearsStaleMappingsWhenMetadataIsEmpty() {
        store.replaceForServer(
                UUID.fromString("8f0904fe-44fd-47f6-a29c-089a534780b1"),
                new NormalizedCapabilityMetadata(List.of(), List.of())
        );

        verify(jdbc, times(3)).update(anyString(), any(SqlParameterSource.class));
        verify(jdbc, never()).queryForObject(anyString(), any(SqlParameterSource.class), eq(UUID.class));
    }

    @Test
    void loadsCanonicalCapabilityNamesForCandidateServersInOneQuery() throws Exception {
        UUID serverId = UUID.fromString("8f0904fe-44fd-47f6-a29c-089a534780b1");
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getObject("server_id", UUID.class)).thenReturn(serverId, serverId);
        when(resultSet.getString("canonical_name"))
                .thenReturn("github.issue.read", "github.issue.comment.create");
        when(jdbc.query(
                anyString(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<ResultSetExtractor<Map<UUID, Set<String>>>>any()
        )).thenAnswer(invocation -> {
            ResultSetExtractor<Map<UUID, Set<String>>> extractor = invocation.getArgument(2);
            return extractor.extractData(resultSet);
        });

        Map<UUID, Set<String>> result = store.findCapabilityNamesByServerIds(List.of(serverId));

        assertThat(result).containsEntry(
                serverId,
                Set.of("github.issue.read", "github.issue.comment.create")
        );
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(
                sql.capture(),
                any(SqlParameterSource.class),
                ArgumentMatchers.<ResultSetExtractor<Map<UUID, Set<String>>>>any()
        );
        assertThat(singleLine(sql.getValue()))
                .contains("FROM mcp_server_capability")
                .contains("WHERE server_capability.server_id IN (:serverIds)");
    }

    private static String singleLine(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
