package dev.mcpcompass.search;

import dev.mcpcompass.embedding.ServerEmbeddingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class SearchDocumentService {
    private final SearchDocumentStore store;
    private final ServerEmbeddingService embeddingService;

    public SearchDocumentService(SearchDocumentStore store, ServerEmbeddingService embeddingService) {
        this.store = store;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public int refreshForRegistryNames(Collection<String> registryNames) {
        List<SearchDocument> documents = store.buildForRegistryNames(registryNames);
        store.replace(documents);
        embeddingService.indexDocuments(documents);
        return documents.size();
    }

    @Transactional
    public int backfill() {
        return backfill(100);
    }

    @Transactional
    public int backfill(int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("batchSize must be between 1 and 200");
        }
        List<SearchDocument> documents = store.buildAll();
        for (int start = 0; start < documents.size(); start += batchSize) {
            List<SearchDocument> batch = documents.subList(start, Math.min(start + batchSize, documents.size()));
            store.replace(batch);
            embeddingService.indexDocuments(batch);
        }
        return documents.size();
    }
}
