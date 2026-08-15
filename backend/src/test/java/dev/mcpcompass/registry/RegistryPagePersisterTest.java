package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistryPagePersisterTest {
    private final McpServerRepository serverRepository = mock(McpServerRepository.class);
    private final RegistrySyncStateRepository stateRepository = mock(RegistrySyncStateRepository.class);
    private final RegistryPagePersister persister = new RegistryPagePersister(serverRepository, stateRepository);

    @Test
    void checkpointsNextCursorWithoutAdvancingWatermark() {
        Instant startedAt = Instant.parse("2026-08-15T10:00:00Z");
        RegistrySyncStateEntity state = RegistrySyncStateEntity.create(RegistrySyncStateEntity.OFFICIAL_REGISTRY);
        state.start(startedAt);

        persister.persist(new RegistryClient.RegistryPage(List.of(), "next"), state, startedAt.plusSeconds(10));

        assertThat(state.getNextCursor()).isEqualTo("next");
        assertThat(state.getUpdatedSince()).isNull();
        assertThat(state.getSyncStartedAt()).isEqualTo(startedAt);
        verify(stateRepository).save(state);
    }

    @Test
    void finalPagePromotesCycleStartToIncrementalWatermark() {
        Instant startedAt = Instant.parse("2026-08-15T10:00:00Z");
        Instant completedAt = startedAt.plusSeconds(30);
        RegistrySyncStateEntity state = RegistrySyncStateEntity.create(RegistrySyncStateEntity.OFFICIAL_REGISTRY);
        state.start(startedAt);
        state.checkpoint("old-cursor");

        persister.persist(new RegistryClient.RegistryPage(List.of(), null), state, completedAt);

        assertThat(state.getNextCursor()).isNull();
        assertThat(state.getUpdatedSince()).isEqualTo(startedAt);
        assertThat(state.getSyncStartedAt()).isNull();
        assertThat(state.getLastSuccessAt()).isEqualTo(completedAt);
        verify(stateRepository).save(state);
    }
}
