package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrySyncStoreTest {
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private final McpServerRepository serverRepository = mock(McpServerRepository.class);
    private final RegistrySyncStateRepository stateRepository = mock(RegistrySyncStateRepository.class);
    private final RegistrySyncStore store = new RegistrySyncStore(serverRepository, stateRepository);

    @Test
    void countsOnlyItemsPersistedFromThePage() {
        RegistryClient.RegistryServerPayload valid = new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}");
        RegistryClient.RegistryServerPayload unnamed = new RegistryClient.RegistryServerPayload(
                " ", "Unnamed", "Description", "1.0.0", "active", "{}");
        RegistryClient.RegistryPage page = new RegistryClient.RegistryPage(
                List.of(valid, unnamed), null);
        when(serverRepository.findByRegistryName(valid.name())).thenReturn(Optional.empty());
        when(stateRepository.findById(RegistrySyncStore.SOURCE)).thenReturn(Optional.empty());

        int persistedItems = store.persistPage(page, NOW, NOW);

        assertThat(persistedItems).isEqualTo(1);
        verify(serverRepository).save(any(McpServerEntity.class));
        verify(serverRepository, never()).findByRegistryName(unnamed.name());
        verify(stateRepository).save(any(RegistrySyncStateEntity.class));
    }
}
