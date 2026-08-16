package dev.mcpcompass.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrySyncService {
    private static final Logger log = LoggerFactory.getLogger(RegistrySyncService.class);

    private final RegistryClient client;
    private final RegistrySyncStore store;
    private final Clock clock;

    public RegistrySyncService(RegistryClient client, RegistrySyncStore store) {
        this(client, store, Clock.systemUTC());
    }

    RegistrySyncService(RegistryClient client, RegistrySyncStore store, Clock clock) {
        this.client = client;
        this.store = store;
        this.clock = clock;
    }

    public SyncResult syncPages(int maxPages) {
        int pageLimit = Math.max(1, maxPages);
        RegistrySyncStore.Checkpoint checkpoint = store.loadCheckpoint();
        String cursor = checkpoint.nextCursor();
        Instant updatedSince = checkpoint.updatedSince();
        Instant syncStartedAt = clock.instant();
        int pages = 0;
        int servers = 0;
        try {
            do {
                RegistryClient.RegistryPage page = client.fetchServers(cursor, updatedSince);
                store.persistPage(page, clock.instant(), syncStartedAt);
                pages++;
                servers += page.servers().size();
                cursor = page.nextCursor();
            } while (cursor != null && !cursor.isBlank() && pages < pageLimit);
        } catch (RuntimeException failure) {
            try {
                store.recordFailure(failure);
            } catch (RuntimeException checkpointFailure) {
                failure.addSuppressed(checkpointFailure);
            }
            throw failure;
        }

        log.info("Registry sync completed: pages={}, servers={}, hasMore={}", pages, servers, cursor != null);
        return new SyncResult(pages, servers, cursor);
    }

    public record SyncResult(int pages, int servers, String nextCursor) {
    }
}
