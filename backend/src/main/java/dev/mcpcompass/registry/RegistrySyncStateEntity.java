package dev.mcpcompass.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "registry_sync_state")
public class RegistrySyncStateEntity {
    public static final String OFFICIAL_REGISTRY = "official-mcp-registry";

    @Id
    @Column(name = "source", nullable = false, length = 128)
    private String source;

    @Column(name = "next_cursor", columnDefinition = "text")
    private String nextCursor;

    @Column(name = "updated_since")
    private Instant updatedSince;

    @Column(name = "sync_started_at")
    private Instant syncStartedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected RegistrySyncStateEntity() {
    }

    public static RegistrySyncStateEntity create(String source) {
        RegistrySyncStateEntity state = new RegistrySyncStateEntity();
        state.source = source;
        return state;
    }

    public void start(Instant startedAt) {
        syncStartedAt = startedAt;
        lastError = null;
    }

    public void checkpoint(String cursor) {
        nextCursor = cursor;
    }

    public void complete(Instant completedAt) {
        updatedSince = syncStartedAt;
        syncStartedAt = null;
        nextCursor = null;
        lastSuccessAt = completedAt;
        lastError = null;
    }

    public String getNextCursor() { return nextCursor; }
    public Instant getUpdatedSince() { return updatedSince; }
    public Instant getSyncStartedAt() { return syncStartedAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
}
