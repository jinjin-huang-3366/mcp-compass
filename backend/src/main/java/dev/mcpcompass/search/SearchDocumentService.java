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
        List<SearchDocument> documents = store.buildAll();
        store.replace(documents);
        embeddingService.indexDocuments(documents);
        return documents.size();
    }
}
