package dev.mcpcompass.registry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "registry_sync_state")
class RegistrySyncStateEntity {
    @Id
    @Column(nullable = false, length = 128)
    private String source;

    @Column(name = "next_cursor", columnDefinition = "text")
    private String nextCursor;

    @Column(name = "updated_since")
    private Instant updatedSince;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    protected RegistrySyncStateEntity() {
    }

    RegistrySyncStateEntity(String source) {
        this.source = source;
    }

    String nextCursor() {
        return nextCursor;
    }

    Instant updatedSince() {
        return updatedSince;
    }

    Instant lastSuccessAt() {
        return lastSuccessAt;
    }

    void pageSucceeded(String nextCursor, Instant completedThrough, Instant successAt) {
        this.nextCursor = nextCursor;
        this.lastError = null;
        if (nextCursor == null) {
            this.updatedSince = completedThrough;
            this.lastSuccessAt = successAt;
        }
    }

    void failed(String error) {
        this.lastError = error;
    }

    void restartFullSync() {
        this.nextCursor = null;
        this.updatedSince = null;
        this.lastError = null;
    }
}
