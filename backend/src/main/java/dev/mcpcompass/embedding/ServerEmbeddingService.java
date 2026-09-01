package dev.mcpcompass.embedding;

import dev.mcpcompass.search.SearchDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ServerEmbeddingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerEmbeddingService.class);

    private final TextEmbeddingProvider provider;
    private final ServerEmbeddingStore store;
    private final EmbeddingProperties properties;

    ServerEmbeddingService(
            TextEmbeddingProvider provider,
            ServerEmbeddingStore store,
            EmbeddingProperties properties
    ) {
        this.provider = provider;
        this.store = store;
        this.properties = properties;
    }

    public void indexDocuments(List<SearchDocument> documents) {
        List<SearchDocument> namedDocuments = documents.stream()
                .filter(document -> document.registryName() != null && !document.registryName().isBlank())
                .toList();
        if (namedDocuments.isEmpty()) {
            return;
        }

        try {
            List<EmbeddingVector> embeddings = provider.embed(namedDocuments.stream()
                    .map(SearchDocument::content)
                    .toList());
            if (embeddings.isEmpty()) {
                return;
            }
            if (embeddings.size() != namedDocuments.size()) {
                throw new IllegalStateException("Embedding provider returned an unexpected result count");
            }
            List<ServerEmbeddingStore.IndexedServerEmbedding> indexed = new ArrayList<>();
            for (int index = 0; index < namedDocuments.size(); index++) {
                indexed.add(new ServerEmbeddingStore.IndexedServerEmbedding(
                        namedDocuments.get(index).registryName(),
                        embeddings.get(index)
                ));
            }
            store.updateByRegistryName(indexed);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Server embedding refresh failed; Registry metadata remains searchable lexically ({})",
                    exception.getClass().getSimpleName()
            );
        }
    }

    public List<ServerEmbeddingMatch> findNearestServers(String requirement) {
        try {
            List<EmbeddingVector> embeddings = provider.embed(List.of(requirement));
            if (embeddings.isEmpty()) {
                return List.of();
            }
            if (embeddings.size() != 1) {
                throw new IllegalStateException("Embedding provider returned an unexpected result count");
            }
            return store.findNearestServers(
                            embeddings.getFirst(),
                            properties.candidateLimit(),
                            properties.minSimilarity()
                    ).stream()
                    .map(nearest -> new ServerEmbeddingMatch(nearest.serverId(), nearest.similarity()))
                    .toList();
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Vector candidate retrieval failed; using lexical candidates only ({})",
                    exception.getClass().getSimpleName()
            );
            return List.of();
        }
    }

    public record ServerEmbeddingMatch(UUID serverId, double similarity) {
    }

}
