package dev.mcpcompass.registry;

import dev.mcpcompass.search.SearchDocumentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionRelevanceActivationServiceTest {
    private final RegistrySyncService registrySyncService = mock(RegistrySyncService.class);
    private final SearchDocumentService searchDocumentService = mock(SearchDocumentService.class);
    private final ProductionRelevanceCoverageStore coverageStore = mock(ProductionRelevanceCoverageStore.class);
    private final ProductionRelevanceActivationService service = new ProductionRelevanceActivationService(
            registrySyncService, searchDocumentService, coverageStore);

    @Test
    void performsBoundedFullResyncThenBackfillAndCoverageSnapshot() {
        var coverage = new ProductionRelevanceCoverageStore.Coverage(12, 10, 8, 12, 12, 9, 7);
        when(registrySyncService.syncPages(20))
                .thenReturn(new RegistrySyncService.SyncResult(20, 2000, "cursor-20"));
        when(registrySyncService.syncPages(5))
                .thenReturn(new RegistrySyncService.SyncResult(5, 400, "cursor-25"));
        when(searchDocumentService.backfill(50)).thenReturn(12);
        when(coverageStore.snapshot()).thenReturn(coverage);

        var result = service.activate(25, 50);

        assertThat(result.registryPages()).isEqualTo(25);
        assertThat(result.registryServers()).isEqualTo(2400);
        assertThat(result.registryHasMore()).isTrue();
        assertThat(result.backfilledDocuments()).isEqualTo(12);
        assertThat(result.coverage()).isEqualTo(coverage);
        var ordered = inOrder(registrySyncService, searchDocumentService, coverageStore);
        ordered.verify(registrySyncService).restartFullSync();
        ordered.verify(registrySyncService).syncPages(20);
        ordered.verify(registrySyncService).syncPages(5);
        ordered.verify(searchDocumentService).backfill(50);
        ordered.verify(coverageStore).snapshot();
    }

    @Test
    void stopsWhenRegistryFullResyncCompletes() {
        when(registrySyncService.syncPages(20))
                .thenReturn(new RegistrySyncService.SyncResult(3, 250, null));
        when(searchDocumentService.backfill(100)).thenReturn(250);
        when(coverageStore.snapshot()).thenReturn(
                new ProductionRelevanceCoverageStore.Coverage(250, 200, 30, 250, 250, 150, 100));

        var result = service.activate(100, 100);

        assertThat(result.registryPages()).isEqualTo(3);
        assertThat(result.registryHasMore()).isFalse();
    }

    @Test
    void rejectsUnboundedPageBudgetsBeforeResettingState() {
        assertThatThrownBy(() -> service.activate(101, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 100");
    }

    @Test
    void rejectsUnboundedEmbeddingBatchesBeforeResettingState() {
        assertThatThrownBy(() -> service.activate(100, 201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 200");
    }
}
