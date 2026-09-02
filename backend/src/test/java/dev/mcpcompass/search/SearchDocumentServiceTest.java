package dev.mcpcompass.search;

import dev.mcpcompass.embedding.ServerEmbeddingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchDocumentServiceTest {
    private final SearchDocumentStore store = mock(SearchDocumentStore.class);
    private final ServerEmbeddingService embeddingService = mock(ServerEmbeddingService.class);
    private final SearchDocumentService service = new SearchDocumentService(store, embeddingService);

    @Test
    void backfillsDocumentsAndEmbeddingsInBoundedBatches() {
        List<SearchDocument> documents = List.of(document("one"), document("two"), document("three"));
        when(store.buildAll()).thenReturn(documents);

        int count = service.backfill(2);

        assertThat(count).isEqualTo(3);
        var ordered = inOrder(store, embeddingService);
        ordered.verify(store).buildAll();
        ordered.verify(store).replace(documents.subList(0, 2));
        ordered.verify(embeddingService).indexDocuments(documents.subList(0, 2));
        ordered.verify(store).replace(documents.subList(2, 3));
        ordered.verify(embeddingService).indexDocuments(documents.subList(2, 3));
    }

    @Test
    void rejectsOversizedProviderBatches() {
        assertThatThrownBy(() -> service.backfill(201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 200");
    }

    private static SearchDocument document(String name) {
        return new SearchDocument(UUID.randomUUID(), name, 1, "service: " + name);
    }
}
