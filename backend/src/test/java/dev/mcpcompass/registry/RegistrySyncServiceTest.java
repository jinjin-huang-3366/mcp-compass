package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class RegistrySyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-15T12:00:00Z");
    private final RegistryClient client = mock(RegistryClient.class);
    private final RegistrySyncStateRepository stateRepository = mock(RegistrySyncStateRepository.class);
    private final RegistryPagePersister pagePersister = mock(RegistryPagePersister.class);
    private final RegistrySyncService service = new RegistrySyncService(
            client, stateRepository, pagePersister, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsInitialTraversalWithoutIncrementalWatermarkAndReturnsCheckpoint() {
        when(stateRepository.findById(RegistrySyncStateEntity.OFFICIAL_REGISTRY)).thenReturn(Optional.empty());
        RegistryClient.RegistryPage page = new RegistryClient.RegistryPage(List.of(server("one")), "cursor-2");
        when(client.fetchServers(null, null)).thenReturn(page);

        RegistrySyncService.SyncResult result = service.syncPages(1);

        assertThat(result).isEqualTo(new RegistrySyncService.SyncResult(1, 1, "cursor-2"));
        verify(client).fetchServers(null, null);
        verify(pagePersister).persist(eq(page), argThat(state -> NOW.equals(state.getSyncStartedAt())), eq(NOW));
    }

    @Test
    void resumesCursorWithLastCompletedUpdatedSinceWatermark() {
        Instant watermark = Instant.parse("2026-08-14T10:00:00Z");
        RegistrySyncStateEntity state = completedState(watermark);
        state.start(NOW.minusSeconds(60));
        state.checkpoint("saved-cursor");
        when(stateRepository.findById(RegistrySyncStateEntity.OFFICIAL_REGISTRY)).thenReturn(Optional.of(state));
        RegistryClient.RegistryPage page = new RegistryClient.RegistryPage(List.of(), null);
        when(client.fetchServers("saved-cursor", watermark)).thenReturn(page);

        RegistrySyncService.SyncResult result = service.syncPages(5);

        assertThat(result.nextCursor()).isNull();
        verify(client).fetchServers("saved-cursor", watermark);
        verify(pagePersister).persist(page, state, NOW);
    }

    private static RegistrySyncStateEntity completedState(Instant watermark) {
        RegistrySyncStateEntity state = RegistrySyncStateEntity.create(RegistrySyncStateEntity.OFFICIAL_REGISTRY);
        state.start(watermark);
        state.complete(watermark.plusSeconds(5));
        return state;
    }

    private static RegistryClient.RegistryServerPayload server(String name) {
        return new RegistryClient.RegistryServerPayload(name, name, null, "1", "active", "{}");
    }
}
