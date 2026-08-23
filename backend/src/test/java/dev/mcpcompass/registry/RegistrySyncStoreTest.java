package dev.mcpcompass.registry;

import dev.mcpcompass.capability.CapabilityMetadataNormalizer;
import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.capability.DeclaredToolSchemaInspector;
import dev.mcpcompass.capability.NormalizedCapabilityMetadata;
import dev.mcpcompass.capability.ToolSchemaInspection;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private final CapabilityMetadataNormalizer capabilityNormalizer = mock(CapabilityMetadataNormalizer.class);
    private final CapabilityMetadataStore capabilityStore = mock(CapabilityMetadataStore.class);
    private final DeclaredToolSchemaInspector toolSchemaInspector = mock(DeclaredToolSchemaInspector.class);
    private final RegistrySyncStore store = new RegistrySyncStore(
            serverRepository,
            stateRepository,
            capabilityNormalizer,
            capabilityStore,
            toolSchemaInspector
    );

    @Test
    void countsOnlyItemsPersistedFromThePage() {
        RegistryClient.RegistryServerPayload valid = new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}");
        RegistryClient.RegistryServerPayload unnamed = new RegistryClient.RegistryServerPayload(
                " ", "Unnamed", "Description", "1.0.0", "active", "{}");
        RegistryClient.RegistryPage page = new RegistryClient.RegistryPage(
                List.of(valid, unnamed), null);
        when(serverRepository.findByRegistryName(valid.name())).thenReturn(Optional.empty());
        when(serverRepository.saveAndFlush(any(McpServerEntity.class))).thenAnswer(invocation -> {
            McpServerEntity entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", UUID.fromString(
                    "f9271073-d3f1-4c62-bbf3-96696e4eeb4f"
            ));
            return entity;
        });
        NormalizedCapabilityMetadata normalized = new NormalizedCapabilityMetadata(List.of(), List.of());
        ToolSchemaInspection inspection = new ToolSchemaInspection(
                ToolSchemaInspection.Status.NOT_DISCOVERABLE, List.of());
        when(toolSchemaInspector.inspect(valid)).thenReturn(inspection);
        when(capabilityNormalizer.normalize(valid, inspection.tools())).thenReturn(normalized);
        when(stateRepository.findById(RegistrySyncStore.SOURCE)).thenReturn(Optional.empty());

        int persistedItems = store.persistPage(page, NOW, NOW);

        assertThat(persistedItems).isEqualTo(1);
        verify(serverRepository).saveAndFlush(any(McpServerEntity.class));
        verify(capabilityStore).replaceForServer(
                UUID.fromString("f9271073-d3f1-4c62-bbf3-96696e4eeb4f"),
                normalized
        );
        verify(toolSchemaInspector).inspect(valid);
        verify(serverRepository, never()).findByRegistryName(unnamed.name());
        verify(stateRepository).save(any(RegistrySyncStateEntity.class));
    }
}
