package dev.mcpcompass.github;

import dev.mcpcompass.registry.RegistryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class GithubEnrichmentService {
    private static final Logger log = LoggerFactory.getLogger(GithubEnrichmentService.class);

    private final GithubEnrichmentProperties properties;
    private final GithubRepositoryClient client;
    private final GithubRepositoryMetricStore store;
    private final GithubRepositoryContentClient contentClient;
    private final GithubRepositoryEnrichmentStore enrichmentStore;
    private final Clock clock;

    @Autowired
    GithubEnrichmentService(
            GithubEnrichmentProperties properties,
            GithubRepositoryClient client,
            GithubRepositoryMetricStore store,
            GithubRepositoryContentClient contentClient,
            GithubRepositoryEnrichmentStore enrichmentStore
    ) {
        this(properties, client, store, contentClient, enrichmentStore, Clock.systemUTC());
    }

    GithubEnrichmentService(
            GithubEnrichmentProperties properties,
            GithubRepositoryClient client,
            GithubRepositoryMetricStore store,
            GithubRepositoryContentClient contentClient,
            GithubRepositoryEnrichmentStore enrichmentStore,
            Clock clock
    ) {
        this.properties = properties;
        this.client = client;
        this.store = store;
        this.contentClient = contentClient;
        this.enrichmentStore = enrichmentStore;
        this.clock = clock;
    }

    public void enrichServers(List<RegistryClient.RegistryServerPayload> servers) {
        if (!properties.enabled()) {
            return;
        }
        for (RegistryClient.RegistryServerPayload server : servers) {
            GithubRepositoryCoordinates.fromUrl(server.repositoryUrl()).ifPresent(coordinates -> {
                try {
                    GithubRepositoryMetadata metadata = client.fetch(coordinates);
                    store.upsert(server.name(), server.repositoryUrl(), metadata, clock.instant());
                    enrichmentStore.replace(server.name(), contentClient.fetch(coordinates), clock.instant());
                } catch (RuntimeException failure) {
                    log.warn("GitHub enrichment failed for Registry server {}: {}",
                            server.name(), failure.getClass().getSimpleName());
                }
            });
        }
    }
}
