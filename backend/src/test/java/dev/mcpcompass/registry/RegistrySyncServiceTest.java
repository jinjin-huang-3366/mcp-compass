package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrySyncServiceTest {
    private static final Instant PREVIOUS_SYNC = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private final RegistryClient client = mock(RegistryClient.class);
    private final RegistrySyncStore store = mock(RegistrySyncStore.class);
    private final RegistrySyncService service = new RegistrySyncService(
            client, store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void resumesCursorAndKeepsUpdatedSinceAcrossPages() {
        RegistryClient.RegistryPage first = page("cursor-2");
        RegistryClient.RegistryPage second = page(null);
        when(store.loadCheckpoint()).thenReturn(new RegistrySyncStore.Checkpoint("cursor-1", PREVIOUS_SYNC));
        when(client.fetchServers("cursor-1", PREVIOUS_SYNC)).thenReturn(first);
        when(client.fetchServers("cursor-2", PREVIOUS_SYNC)).thenReturn(second);

        RegistrySyncService.SyncResult result = service.syncPages(2);

        assertThat(result).isEqualTo(new RegistrySyncService.SyncResult(2, 2, null));
        var ordered = inOrder(client, store);
        ordered.verify(client).fetchServers("cursor-1", PREVIOUS_SYNC);
        ordered.verify(store).persistPage(first, NOW, NOW);
        ordered.verify(client).fetchServers("cursor-2", PREVIOUS_SYNC);
        ordered.verify(store).persistPage(second, NOW, NOW);
    }

    @Test
    void stopsAtPageLimitAndReturnsPersistedContinuationCursor() {
        RegistryClient.RegistryPage page = page("cursor-2");
        when(store.loadCheckpoint()).thenReturn(new RegistrySyncStore.Checkpoint(null, PREVIOUS_SYNC));
        when(client.fetchServers(null, PREVIOUS_SYNC)).thenReturn(page);

        RegistrySyncService.SyncResult result = service.syncPages(1);

        assertThat(result.nextCursor()).isEqualTo("cursor-2");
        verify(store).persistPage(page, NOW, NOW);
    }

    @Test
    void recordsFailureWithoutAdvancingTheCheckpoint() {
        IllegalStateException failure = new IllegalStateException("Registry unavailable");
        when(store.loadCheckpoint()).thenReturn(new RegistrySyncStore.Checkpoint("cursor-1", PREVIOUS_SYNC));
        when(client.fetchServers("cursor-1", PREVIOUS_SYNC)).thenThrow(failure);

        assertThatThrownBy(() -> service.syncPages(1)).isSameAs(failure);

        verify(store).recordFailure(failure);
        verify(store, org.mockito.Mockito.never()).persistPage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static RegistryClient.RegistryPage page(String cursor) {
        return new RegistryClient.RegistryPage(List.of(new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}")), cursor);
    }
}
