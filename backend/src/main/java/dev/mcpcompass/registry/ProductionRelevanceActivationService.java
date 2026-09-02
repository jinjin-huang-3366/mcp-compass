package dev.mcpcompass.registry;

import dev.mcpcompass.search.SearchDocumentService;
import org.springframework.stereotype.Service;

@Service
class ProductionRelevanceActivationService {
    private static final int SYNC_PAGE_BATCH = 20;

    private final RegistrySyncService registrySyncService;
    private final SearchDocumentService searchDocumentService;
    private final ProductionRelevanceCoverageStore coverageStore;

    ProductionRelevanceActivationService(
            RegistrySyncService registrySyncService,
            SearchDocumentService searchDocumentService,
            ProductionRelevanceCoverageStore coverageStore
    ) {
        this.registrySyncService = registrySyncService;
        this.searchDocumentService = searchDocumentService;
        this.coverageStore = coverageStore;
    }

    ActivationResult activate(int maxPages, int embeddingBatchSize) {
        if (maxPages < 1 || maxPages > 100) {
            throw new IllegalArgumentException("maxPages must be between 1 and 100");
        }
        if (embeddingBatchSize < 1 || embeddingBatchSize > 200) {
            throw new IllegalArgumentException("embeddingBatchSize must be between 1 and 200");
        }
        registrySyncService.restartFullSync();
        int pages = 0;
        int servers = 0;
        String nextCursor = null;
        do {
            RegistrySyncService.SyncResult batch = registrySyncService.syncPages(
                    Math.min(SYNC_PAGE_BATCH, maxPages - pages));
            pages += batch.pages();
            servers += batch.servers();
            nextCursor = batch.nextCursor();
        } while (nextCursor != null && !nextCursor.isBlank() && pages < maxPages);

        int documents = searchDocumentService.backfill(embeddingBatchSize);
        return new ActivationResult(pages, servers, nextCursor != null && !nextCursor.isBlank(), documents,
                coverageStore.snapshot());
    }

    record ActivationResult(
            int registryPages,
            int registryServers,
            boolean registryHasMore,
            int backfilledDocuments,
            ProductionRelevanceCoverageStore.Coverage coverage
    ) {
    }
}
