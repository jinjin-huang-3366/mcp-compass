package dev.mcpcompass.capability;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
class JdbcCapabilityMetadataStore implements CapabilityMetadataStore {
    private static final String UPSERT_CAPABILITY = """
            INSERT INTO capability (id, canonical_name, description)
            VALUES (:id, :canonicalName, :description)
            ON CONFLICT (canonical_name) DO UPDATE
            SET description = COALESCE(EXCLUDED.description, capability.description)
            RETURNING id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    JdbcCapabilityMetadataStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replaceForServer(UUID serverId, NormalizedCapabilityMetadata metadata) {
        Objects.requireNonNull(serverId, "serverId must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Map<String, UUID> capabilityIds = new HashMap<>();
        MapSqlParameterSource serverParameters = new MapSqlParameterSource("serverId", serverId);

        jdbc.update("""
                DELETE FROM mcp_tool_capability
                WHERE tool_id IN (SELECT id FROM mcp_tool WHERE server_id = :serverId)
                """, serverParameters);
        jdbc.update("DELETE FROM mcp_tool WHERE server_id = :serverId", serverParameters);
        jdbc.update("DELETE FROM mcp_server_capability WHERE server_id = :serverId", serverParameters);

        for (NormalizedCapabilityMetadata.NormalizedCapability capability : metadata.serverCapabilities()) {
            UUID capabilityId = capabilityId(capabilityIds, capability);
            jdbc.update("""
                    INSERT INTO mcp_server_capability
                        (server_id, capability_id, confidence, source)
                    VALUES (:serverId, :capabilityId, :confidence, :source)
                    """, new MapSqlParameterSource()
                    .addValue("serverId", serverId)
                    .addValue("capabilityId", capabilityId)
                    .addValue("confidence", capability.confidence())
                    .addValue("source", capability.source()));
        }

        for (NormalizedCapabilityMetadata.NormalizedTool tool : metadata.tools()) {
            UUID toolId = deterministicUuid("mcp-tool", serverId + "\\0" + tool.name());
            jdbc.update("""
                    INSERT INTO mcp_tool
                        (id, server_id, name, description, input_schema, risk_level)
                    VALUES (:id, :serverId, :name, :description, :inputSchema, NULL)
                    """, new MapSqlParameterSource()
                    .addValue("id", toolId)
                    .addValue("serverId", serverId)
                    .addValue("name", tool.name())
                    .addValue("description", tool.description())
                    .addValue("inputSchema", tool.inputSchema()));

            for (NormalizedCapabilityMetadata.NormalizedCapability capability : tool.capabilities()) {
                UUID capabilityId = capabilityId(capabilityIds, capability);
                jdbc.update("""
                        INSERT INTO mcp_tool_capability
                            (tool_id, capability_id, confidence, source)
                        VALUES (:toolId, :capabilityId, :confidence, :source)
                        """, new MapSqlParameterSource()
                        .addValue("toolId", toolId)
                        .addValue("capabilityId", capabilityId)
                        .addValue("confidence", capability.confidence())
                        .addValue("source", capability.source()));
            }
        }
    }

    @Override
    public Map<UUID, Set<String>> findCapabilityNamesByServerIds(Collection<UUID> serverIds) {
        Objects.requireNonNull(serverIds, "serverIds must not be null");
        if (serverIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, Set<String>> capabilitiesByServer = jdbc.query("""
                SELECT server_capability.server_id, capability.canonical_name
                FROM mcp_server_capability server_capability
                JOIN capability ON capability.id = server_capability.capability_id
                WHERE server_capability.server_id IN (:serverIds)
                ORDER BY server_capability.server_id, capability.canonical_name
                """, new MapSqlParameterSource("serverIds", serverIds), resultSet -> {
            Map<UUID, Set<String>> result = new HashMap<>();
            while (resultSet.next()) {
                result.computeIfAbsent(
                        resultSet.getObject("server_id", UUID.class),
                        ignored -> new LinkedHashSet<>()
                ).add(resultSet.getString("canonical_name"));
            }
            return result;
        });

        capabilitiesByServer.replaceAll((serverId, capabilityNames) -> Set.copyOf(capabilityNames));
        return Map.copyOf(capabilitiesByServer);
    }

    private UUID capabilityId(
            Map<String, UUID> capabilityIds,
            NormalizedCapabilityMetadata.NormalizedCapability capability
    ) {
        return capabilityIds.computeIfAbsent(capability.canonicalName(), ignored -> {
            UUID id = deterministicUuid("capability", capability.canonicalName());
            UUID persistedId = jdbc.queryForObject(
                    UPSERT_CAPABILITY,
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("canonicalName", capability.canonicalName())
                            .addValue("description", capability.description()),
                    UUID.class
            );
            return Objects.requireNonNull(persistedId, "Capability upsert did not return an id");
        });
    }

    private static UUID deterministicUuid(String namespace, String value) {
        return UUID.nameUUIDFromBytes(
                (namespace + "\\0" + value).getBytes(StandardCharsets.UTF_8)
        );
    }
}
