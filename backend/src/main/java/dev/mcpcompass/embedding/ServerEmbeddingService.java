package dev.mcpcompass.embedding;

import dev.mcpcompass.registry.RegistryClient;
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

    public void indexServers(List<RegistryClient.RegistryServerPayload> servers) {
        List<RegistryClient.RegistryServerPayload> namedServers = servers.stream()
                .filter(server -> server.name() != null && !server.name().isBlank())
                .toList();
        if (namedServers.isEmpty()) {
            return;
        }

        try {
            List<EmbeddingVector> embeddings = provider.embed(namedServers.stream()
                    .map(ServerEmbeddingService::searchDocument)
                    .toList());
            if (embeddings.isEmpty()) {
                return;
            }
            if (embeddings.size() != namedServers.size()) {
                throw new IllegalStateException("Embedding provider returned an unexpected result count");
            }
            List<ServerEmbeddingStore.IndexedServerEmbedding> indexed = new ArrayList<>();
            for (int index = 0; index < namedServers.size(); index++) {
                indexed.add(new ServerEmbeddingStore.IndexedServerEmbedding(
                        namedServers.get(index).name(),
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

    private static String searchDocument(RegistryClient.RegistryServerPayload server) {
        return String.join("\n", value(server.name()), value(server.title()), value(server.description()));
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
