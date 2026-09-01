package dev.mcpcompass.embedding;

import dev.mcpcompass.search.SearchDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerEmbeddingServiceTest {
    private final TextEmbeddingProvider provider = mock(TextEmbeddingProvider.class);
    private final ServerEmbeddingStore store = mock(ServerEmbeddingStore.class);
    private final EmbeddingProperties properties = new EmbeddingProperties(
            true, "https://api.openai.com", "test-key", "text-embedding-3-small",
            Duration.ofSeconds(1), Duration.ofSeconds(1), 25, 0.4
    );
    private final ServerEmbeddingService service = new ServerEmbeddingService(provider, store, properties);

    @Test
    void batchesServerDocumentsAndPersistsEmbeddingsInInputOrder() {
        SearchDocument first = document("io.example/github", "service: GitHub\ntool: list_issues");
        SearchDocument second = document("io.example/slack", "service: Slack\ntool: send_message");
        EmbeddingVector firstVector = vector(0.1);
        EmbeddingVector secondVector = vector(0.2);
        when(provider.embed(List.of(
                "service: GitHub\ntool: list_issues",
                "service: Slack\ntool: send_message"
        ))).thenReturn(List.of(firstVector, secondVector));

        service.indexDocuments(List.of(first, second));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ServerEmbeddingStore.IndexedServerEmbedding>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(store).updateByRegistryName(captor.capture());
        assertThat(captor.getValue())
                .extracting(ServerEmbeddingStore.IndexedServerEmbedding::registryName)
                .containsExactly("io.example/github", "io.example/slack");
        assertThat(captor.getValue())
                .extracting(ServerEmbeddingStore.IndexedServerEmbedding::embedding)
                .containsExactly(firstVector, secondVector);
    }

    @Test
    void fallsBackToLexicalRetrievalWhenEmbeddingFails() {
        when(provider.embed(List.of("github issues"))).thenThrow(new IllegalStateException("unavailable"));

        assertThat(service.findNearestServers("github issues")).isEmpty();

        verify(store, never()).findNearestServers(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyDouble()
        );
    }

    @Test
    void usesConfiguredBoundForNearestCandidates() {
        EmbeddingVector query = vector(0.3);
        UUID serverId = UUID.fromString("e4c3efb5-e259-4d7a-9969-62baa0938c5e");
        when(provider.embed(List.of("github issues"))).thenReturn(List.of(query));
        when(store.findNearestServers(query, 25, 0.4))
                .thenReturn(List.of(new ServerEmbeddingStore.NearestServer(serverId, 0.8)));

        assertThat(service.findNearestServers("github issues"))
                .containsExactly(new ServerEmbeddingService.ServerEmbeddingMatch(serverId, 0.8));
    }

    private static SearchDocument document(String registryName, String content) {
        return new SearchDocument(UUID.randomUUID(), registryName, 1, content);
    }

    private static EmbeddingVector vector(double firstValue) {
        List<Double> values = new java.util.ArrayList<>(
                Collections.nCopies(EmbeddingProperties.DIMENSIONS, 0.0)
        );
        values.set(0, firstValue);
        return new EmbeddingVector("text-embedding-3-small", values);
    }
}
