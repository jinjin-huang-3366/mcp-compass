package dev.mcpcompass.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class RegistryPagePersister {
    private static final Logger log = LoggerFactory.getLogger(RegistryPagePersister.class);

    private final McpServerRepository serverRepository;
    private final RegistrySyncStateRepository stateRepository;

    public RegistryPagePersister(McpServerRepository serverRepository, RegistrySyncStateRepository stateRepository) {
        this.serverRepository = serverRepository;
        this.stateRepository = stateRepository;
    }

    @Transactional
    public void persist(RegistryClient.RegistryPage page, RegistrySyncStateEntity state, Instant now) {
        for (RegistryClient.RegistryServerPayload payload : page.servers()) {
            if (payload.name() == null || payload.name().isBlank()) {
                log.warn("Skipping Registry server without name");
                continue;
            }
            McpServerEntity entity = serverRepository.findByRegistryName(payload.name())
                    .orElseGet(() -> McpServerEntity.create(payload.name(), now));
            entity.updateFrom(payload, now);
            serverRepository.save(entity);
        }

        if (page.nextCursor() == null || page.nextCursor().isBlank()) {
            state.complete(now);
        } else {
            state.checkpoint(page.nextCursor());
        }
        stateRepository.save(state);
    }
}
