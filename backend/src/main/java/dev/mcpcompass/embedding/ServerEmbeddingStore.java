package dev.mcpcompass.embedding;

import java.util.List;
import java.util.UUID;

interface ServerEmbeddingStore {
    void updateByRegistryName(List<IndexedServerEmbedding> embeddings);

    List<NearestServer> findNearestServers(
            EmbeddingVector queryEmbedding,
            int limit,
            double minSimilarity
    );

    record IndexedServerEmbedding(String registryName, EmbeddingVector embedding) {
    }

    record NearestServer(UUID serverId, double similarity) {
    }
}
