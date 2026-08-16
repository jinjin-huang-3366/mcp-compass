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
    private final RegistrySyncMetrics metrics;
    private final Clock clock;

    public RegistrySyncService(
            RegistryClient client,
            RegistrySyncStore store,
            RegistrySyncMetrics metrics
    ) {
        this(client, store, metrics, Clock.systemUTC());
    }

    RegistrySyncService(
            RegistryClient client,
            RegistrySyncStore store,
            RegistrySyncMetrics metrics,
            Clock clock
    ) {
        this.client = client;
        this.store = store;
        this.metrics = metrics;
        this.clock = clock;
    }

    public SyncResult syncPages(int maxPages) {
        int pageLimit = Math.max(1, maxPages);
        String cursor = null;
        int pages = 0;
        int servers = 0;
        try {
            RegistrySyncStore.Checkpoint checkpoint = store.loadCheckpoint();
            cursor = checkpoint.nextCursor();
            Instant updatedSince = checkpoint.updatedSince();
            metrics.restoreLastSuccess(checkpoint.lastSuccessAt());
            Instant syncStartedAt = clock.instant();

            do {
                RegistryClient.RegistryPage page = client.fetchServers(cursor, updatedSince);
                int persistedItems = store.persistPage(page, clock.instant(), syncStartedAt);
                pages++;
                servers += page.servers().size();
                metrics.recordPage(persistedItems);
                cursor = page.nextCursor();
            } while (hasMore(cursor) && pages < pageLimit);

            if (!hasMore(cursor)) {
                metrics.recordCompletedSync(clock.instant());
            }
        } catch (RuntimeException failure) {
            metrics.recordError();
            try {
                store.recordFailure(failure);
            } catch (RuntimeException checkpointFailure) {
                failure.addSuppressed(checkpointFailure);
            }
            throw failure;
        }

        log.info("Registry sync completed: pages={}, servers={}, hasMore={}", pages, servers, hasMore(cursor));
        return new SyncResult(pages, servers, cursor);
    }

    private static boolean hasMore(String cursor) {
        return cursor != null && !cursor.isBlank();
    }

    public record SyncResult(int pages, int servers, String nextCursor) {
    }
}
