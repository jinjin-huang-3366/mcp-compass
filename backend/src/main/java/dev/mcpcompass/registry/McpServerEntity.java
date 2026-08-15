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
        this.lastSeenAt = now;
    }

    public UUID getId() { return id; }
    public String getRegistryName() { return registryName; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getVersion() { return version; }
    public String getStatus() { return status; }
    public String getRawMetadata() { return rawMetadata; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
