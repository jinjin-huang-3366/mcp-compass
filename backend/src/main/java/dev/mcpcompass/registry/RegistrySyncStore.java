package dev.mcpcompass.registry;

import dev.mcpcompass.capability.CapabilityMetadataNormalizer;
import dev.mcpcompass.capability.CapabilityMetadataStore;
import dev.mcpcompass.capability.DeclaredToolSchemaInspector;
import dev.mcpcompass.capability.ToolSchemaInspection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
class RegistrySyncStore {
    static final String SOURCE = "official-mcp-registry";
    private static final Logger log = LoggerFactory.getLogger(RegistrySyncStore.class);

    private final McpServerRepository serverRepository;
    private final RegistrySyncStateRepository stateRepository;
    private final CapabilityMetadataNormalizer capabilityNormalizer;
    private final CapabilityMetadataStore capabilityStore;
    private final DeclaredToolSchemaInspector toolSchemaInspector;

    RegistrySyncStore(
            McpServerRepository serverRepository,
            RegistrySyncStateRepository stateRepository,
            CapabilityMetadataNormalizer capabilityNormalizer,
            CapabilityMetadataStore capabilityStore,
            DeclaredToolSchemaInspector toolSchemaInspector
    ) {
        this.serverRepository = serverRepository;
        this.stateRepository = stateRepository;
        this.capabilityNormalizer = capabilityNormalizer;
        this.capabilityStore = capabilityStore;
        this.toolSchemaInspector = toolSchemaInspector;
    }

    @Transactional(readOnly = true)
    Checkpoint loadCheckpoint() {
        return stateRepository.findById(SOURCE)
                .map(state -> new Checkpoint(
                        state.nextCursor(), state.updatedSince(), state.lastSuccessAt()))
                .orElseGet(() -> new Checkpoint(null, null, null));
    }

    @Transactional
    int persistPage(RegistryClient.RegistryPage page, Instant seenAt, Instant syncStartedAt) {
        int persistedItems = 0;
        for (RegistryClient.RegistryServerPayload payload : page.servers()) {
            if (payload.name() == null || payload.name().isBlank()) {
                log.warn("Skipping Registry server without name");
                continue;
            }
            McpServerEntity entity = serverRepository.findByRegistryName(payload.name())
                    .orElseGet(() -> McpServerEntity.create(payload.name(), seenAt));
            entity.updateFrom(payload, seenAt);
            ToolSchemaInspection inspection = toolSchemaInspector.inspect(payload);
            entity.recordToolSchemaInspection(inspection.status().name(), seenAt);
            McpServerEntity persistedServer = serverRepository.saveAndFlush(entity);
            capabilityStore.replaceForServer(
                    persistedServer.getId(),
                    capabilityNormalizer.normalize(payload, inspection.tools())
            );
            persistedItems++;
        }

        String nextCursor = normalize(page.nextCursor());
        RegistrySyncStateEntity state = stateRepository.findById(SOURCE)
                .orElseGet(() -> new RegistrySyncStateEntity(SOURCE));
        state.pageSucceeded(nextCursor, syncStartedAt, seenAt);
        stateRepository.save(state);
        return persistedItems;
    }

    @Transactional
    void recordFailure(RuntimeException failure) {
        RegistrySyncStateEntity state = stateRepository.findById(SOURCE)
                .orElseGet(() -> new RegistrySyncStateEntity(SOURCE));
        state.failed(failure.getMessage());
        stateRepository.save(state);
    }

    @Transactional
    void restartFullSync() {
        RegistrySyncStateEntity state = stateRepository.findById(SOURCE)
                .orElseGet(() -> new RegistrySyncStateEntity(SOURCE));
        state.restartFullSync();
        stateRepository.save(state);
    }

    private static String normalize(String cursor) {
        return cursor == null || cursor.isBlank() ? null : cursor;
    }

    record Checkpoint(String nextCursor, Instant updatedSince, Instant lastSuccessAt) {
    }
}
