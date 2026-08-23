package dev.mcpcompass.registry;

import dev.mcpcompass.embedding.ServerEmbeddingService;
import dev.mcpcompass.github.GithubEnrichmentService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrySyncServiceTest {
    private static final Instant PREVIOUS_SYNC = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-15T10:00:00Z");
    private final RegistryClient client = mock(RegistryClient.class);
    private final RegistrySyncStore store = mock(RegistrySyncStore.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RegistrySyncMetrics metrics = new RegistrySyncMetrics(meterRegistry);
    private final ServerEmbeddingService embeddingService = mock(ServerEmbeddingService.class);
    private final GithubEnrichmentService githubEnrichmentService = mock(GithubEnrichmentService.class);
    private final RegistrySyncService service = new RegistrySyncService(
            client, store, metrics, embeddingService, githubEnrichmentService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void springCreatesServiceWithDependencyConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RegistryClient.class, () -> client);
            context.registerBean(RegistrySyncStore.class, () -> store);
            context.registerBean(RegistrySyncMetrics.class, () -> metrics);
            context.registerBean(ServerEmbeddingService.class, () -> embeddingService);
            context.registerBean(GithubEnrichmentService.class, () -> githubEnrichmentService);
            context.register(RegistrySyncService.class);

            context.refresh();

            assertThat(context.getBean(RegistrySyncService.class)).isNotNull();
        }
    }

    @Test
    void resumesCursorAndRecordsCompletedSyncMetrics() {
        RegistryClient.RegistryPage first = page("cursor-2");
        RegistryClient.RegistryPage second = page(null);
        when(store.loadCheckpoint()).thenReturn(
                new RegistrySyncStore.Checkpoint("cursor-1", PREVIOUS_SYNC, PREVIOUS_SYNC));
        when(client.fetchServers("cursor-1", PREVIOUS_SYNC)).thenReturn(first);
        when(client.fetchServers("cursor-2", PREVIOUS_SYNC)).thenReturn(second);
        when(store.persistPage(first, NOW, NOW)).thenReturn(1);
        when(store.persistPage(second, NOW, NOW)).thenReturn(1);

        RegistrySyncService.SyncResult result = service.syncPages(2);

        assertThat(result).isEqualTo(new RegistrySyncService.SyncResult(2, 2, null));
        var ordered = inOrder(client, store, embeddingService, githubEnrichmentService);
        ordered.verify(client).fetchServers("cursor-1", PREVIOUS_SYNC);
        ordered.verify(store).persistPage(first, NOW, NOW);
        ordered.verify(embeddingService).indexServers(first.servers());
        ordered.verify(githubEnrichmentService).enrichServers(first.servers());
        ordered.verify(client).fetchServers("cursor-2", PREVIOUS_SYNC);
        ordered.verify(store).persistPage(second, NOW, NOW);
        ordered.verify(embeddingService).indexServers(second.servers());
        ordered.verify(githubEnrichmentService).enrichServers(second.servers());
        assertThat(counter(RegistrySyncMetrics.PAGES)).isEqualTo(2);
        assertThat(counter(RegistrySyncMetrics.ITEMS)).isEqualTo(2);
        assertThat(counter(RegistrySyncMetrics.ERRORS)).isZero();
        assertThat(gauge(RegistrySyncMetrics.LAST_SUCCESS)).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    void stopsAtPageLimitWithoutAdvancingLastSuccessfulSync() {
        RegistryClient.RegistryPage page = page("cursor-2");
        when(store.loadCheckpoint()).thenReturn(
                new RegistrySyncStore.Checkpoint(null, PREVIOUS_SYNC, PREVIOUS_SYNC));
        when(client.fetchServers(null, PREVIOUS_SYNC)).thenReturn(page);
        when(store.persistPage(page, NOW, NOW)).thenReturn(1);

        RegistrySyncService.SyncResult result = service.syncPages(1);

        assertThat(result.nextCursor()).isEqualTo("cursor-2");
        verify(store).persistPage(page, NOW, NOW);
        assertThat(counter(RegistrySyncMetrics.PAGES)).isEqualTo(1);
        assertThat(counter(RegistrySyncMetrics.ITEMS)).isEqualTo(1);
        assertThat(gauge(RegistrySyncMetrics.LAST_SUCCESS))
                .isEqualTo(PREVIOUS_SYNC.getEpochSecond());
    }

    @Test
    void recordsFailureMetricWithoutAdvancingTheCheckpoint() {
        IllegalStateException failure = new IllegalStateException("Registry unavailable");
        when(store.loadCheckpoint()).thenReturn(
                new RegistrySyncStore.Checkpoint("cursor-1", PREVIOUS_SYNC, PREVIOUS_SYNC));
        when(client.fetchServers("cursor-1", PREVIOUS_SYNC)).thenThrow(failure);

        assertThatThrownBy(() -> service.syncPages(1)).isSameAs(failure);

        verify(store).recordFailure(failure);
        verify(store, org.mockito.Mockito.never()).persistPage(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        assertThat(counter(RegistrySyncMetrics.PAGES)).isZero();
        assertThat(counter(RegistrySyncMetrics.ITEMS)).isZero();
        assertThat(counter(RegistrySyncMetrics.ERRORS)).isEqualTo(1);
        assertThat(gauge(RegistrySyncMetrics.LAST_SUCCESS))
                .isEqualTo(PREVIOUS_SYNC.getEpochSecond());
    }

    private double counter(String name) {
        return meterRegistry.get(name)
                .tag(RegistrySyncMetrics.SOURCE_TAG, RegistrySyncStore.SOURCE)
                .counter()
                .count();
    }

    private double gauge(String name) {
        return meterRegistry.get(name)
                .tag(RegistrySyncMetrics.SOURCE_TAG, RegistrySyncStore.SOURCE)
                .gauge()
                .value();
    }

    private static RegistryClient.RegistryPage page(String cursor) {
        return new RegistryClient.RegistryPage(List.of(new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}")), cursor);
    }
}
