package dev.mcpcompass.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class RegistrySyncService {
    private static final Logger log = LoggerFactory.getLogger(RegistrySyncService.class);

    private final RegistryClient client;
    private final RegistrySyncStateRepository stateRepository;
    private final RegistryPagePersister pagePersister;
    private final Clock clock;

    public RegistrySyncService(RegistryClient client, RegistrySyncStateRepository stateRepository,
                               RegistryPagePersister pagePersister) {
        this(client, stateRepository, pagePersister, Clock.systemUTC());
    }

    RegistrySyncService(RegistryClient client, RegistrySyncStateRepository stateRepository,
                        RegistryPagePersister pagePersister, Clock clock) {
        this.client = client;
        this.stateRepository = stateRepository;
        this.pagePersister = pagePersister;
        this.clock = clock;
    }

    public SyncResult syncPages(int maxPages) {
        int pageLimit = Math.max(1, maxPages);
        RegistrySyncStateEntity state = stateRepository.findById(RegistrySyncStateEntity.OFFICIAL_REGISTRY)
                .orElseGet(() -> RegistrySyncStateEntity.create(RegistrySyncStateEntity.OFFICIAL_REGISTRY));
        if (state.getSyncStartedAt() == null) {
            state.start(clock.instant());
            stateRepository.save(state);
        }
        String cursor = state.getNextCursor();
        int pages = 0;
        int servers = 0;
        do {
            RegistryClient.RegistryPage page = client.fetchServers(cursor, state.getUpdatedSince());
            pagePersister.persist(page, state, clock.instant());
            pages++;
            servers += page.servers().size();
            cursor = page.nextCursor();
        } while (cursor != null && !cursor.isBlank() && pages < pageLimit);

        log.info("Registry sync completed: pages={}, servers={}, hasMore={}", pages, servers, cursor != null);
        return new SyncResult(pages, servers, cursor);
    }

    public record SyncResult(int pages, int servers, String nextCursor) {
    }
}
