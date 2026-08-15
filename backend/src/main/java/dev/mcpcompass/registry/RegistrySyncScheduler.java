package dev.mcpcompass.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.registry.sync", name = "enabled", havingValue = "true")
public class RegistrySyncScheduler {
    private final RegistrySyncService service;
    private final RegistryProperties properties;

    public RegistrySyncScheduler(RegistrySyncService service, RegistryProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.registry.sync.fixed-delay-ms:3600000}")
    public void sync() {
        service.syncPages(properties.sync().maxPagesPerRun());
    }
}
