package dev.mcpcompass.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_server")
public class McpServerEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "registry_name", nullable = false, unique = true)
    private String registryName;

    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private String version;
    private String status;

    @Column(name = "raw_metadata", columnDefinition = "text")
    private String rawMetadata;

    @Column(name = "official_registry_provenance", nullable = false)
    private boolean officialRegistryProvenance;

    @Column(name = "repository_url", columnDefinition = "text")
    private String repositoryUrl;

    @Column(name = "package_count", nullable = false)
    private int packageCount;

    @Column(name = "remote_count", nullable = false)
    private int remoteCount;

    @Column(name = "tool_schema_status", nullable = false)
    private String toolSchemaStatus = "NOT_DISCOVERABLE";

    @Column(name = "tool_schema_inspected_at")
    private Instant toolSchemaInspectedAt;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected McpServerEntity() {
    }

    public static McpServerEntity create(String registryName, Instant now) {
        McpServerEntity entity = new McpServerEntity();
        entity.registryName = registryName;
        entity.firstSeenAt = now;
        entity.lastSeenAt = now;
        return entity;
    }

    public void updateFrom(RegistryClient.RegistryServerPayload payload, Instant now) {
        this.title = payload.title();
        this.description = payload.description();
        this.version = payload.version();
        this.status = payload.status();
        this.rawMetadata = payload.rawMetadata();
        this.officialRegistryProvenance = payload.officialRegistryProvenance();
        this.repositoryUrl = payload.repositoryUrl();
        this.packageCount = payload.packageCount();
        this.remoteCount = payload.remoteCount();
        this.lastSeenAt = now;
    }

    public void recordToolSchemaInspection(String status, Instant inspectedAt) {
        this.toolSchemaStatus = status;
        this.toolSchemaInspectedAt = inspectedAt;
    }

    public UUID getId() { return id; }
    public String getRegistryName() { return registryName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public String getStatus() { return status; }
    public String getRawMetadata() { return rawMetadata; }
    public boolean hasOfficialRegistryProvenance() { return officialRegistryProvenance; }
    public String getRepositoryUrl() { return repositoryUrl; }
    public int getPackageCount() { return packageCount; }
    public int getRemoteCount() { return remoteCount; }
    public String getToolSchemaStatus() { return toolSchemaStatus; }
    public Instant getToolSchemaInspectedAt() { return toolSchemaInspectedAt; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
