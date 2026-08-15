package dev.mcpcompass.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrySyncService {
    private static final Logger log = LoggerFactory.getLogger(RegistrySyncService.class);

    private final RegistryClient client;
    private final McpServerRepository repository;
    private final Clock clock;

    @Autowired
    public RegistrySyncService(RegistryClient client, McpServerRepository repository) {
        this(client, repository, Clock.systemUTC());
    }

    RegistrySyncService(RegistryClient client, McpServerRepository repository, Clock clock) {
        this.client = client;
        this.repository = repository;
        this.clock = clock;
    }

    public SyncResult syncPages(int maxPages) {
        int pageLimit = Math.max(1, maxPages);
        String cursor = null;
        int pages = 0;
        int servers = 0;
        do {
            RegistryClient.RegistryPage page = client.fetchServers(cursor);
            persist(page);
            pages++;
            servers += page.servers().size();
            cursor = page.nextCursor();
        } while (cursor != null && !cursor.isBlank() && pages < pageLimit);

        log.info("Registry sync completed: pages={}, servers={}, hasMore={}", pages, servers, cursor != null);
        return new SyncResult(pages, servers, cursor);
    }

    protected void persist(RegistryClient.RegistryPage page) {
        Instant now = clock.instant();
        for (RegistryClient.RegistryServerPayload payload : page.servers()) {
            if (payload.name() == null || payload.name().isBlank()) {
                log.warn("Skipping Registry server without name");
                continue;
            }
            McpServerEntity entity = repository.findByRegistryName(payload.name())
                    .orElseGet(() -> McpServerEntity.create(payload.name(), now));
            entity.updateFrom(payload, now);
            repository.save(entity);
        }
    }

    public record SyncResult(int pages, int servers, String nextCursor) {
    }
}
