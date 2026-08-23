package dev.mcpcompass.github;

import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubEnrichmentServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-23T10:00:00Z");
    private final GithubRepositoryClient client = mock(GithubRepositoryClient.class);
    private final GithubRepositoryMetricStore store = mock(GithubRepositoryMetricStore.class);

    @Test
    void enrichesGithubRepositoriesAndPersistsCollectionTime() {
        GithubRepositoryMetadata metadata = new GithubRepositoryMetadata(NOW, null, false, "MIT");
        GithubRepositoryCoordinates coordinates = new GithubRepositoryCoordinates("example", "server");
        when(client.fetch(coordinates)).thenReturn(metadata);
        GithubEnrichmentService service = service(true);

        service.enrichServers(List.of(payload("https://github.com/example/server")));

        verify(store).upsert("io.example/server", "https://github.com/example/server", metadata, NOW);
    }

    @Test
    void isolatesGithubFailuresAndSkipsUnsupportedUrls() {
        GithubRepositoryCoordinates coordinates = new GithubRepositoryCoordinates("example", "server");
        when(client.fetch(coordinates)).thenThrow(new IllegalStateException("rate limited"));
        GithubEnrichmentService service = service(true);

        service.enrichServers(List.of(
                payload("https://github.com/example/server"),
                payload("https://gitlab.com/example/server")
        ));

        verify(store, never()).upsert(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void makesNoExternalCallsWhenDisabled() {
        service(false).enrichServers(List.of(payload("https://github.com/example/server")));

        verify(client, never()).fetch(org.mockito.ArgumentMatchers.any());
    }

    private GithubEnrichmentService service(boolean enabled) {
        GithubEnrichmentProperties properties = new GithubEnrichmentProperties(
                enabled, "https://api.github.com", "", Duration.ofSeconds(1), Duration.ofSeconds(2));
        return new GithubEnrichmentService(
                properties, client, store, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static RegistryClient.RegistryServerPayload payload(String repositoryUrl) {
        return new RegistryClient.RegistryServerPayload(
                "io.example/server", "Example", "Description", "1.0.0", "active", "{}",
                true, repositoryUrl, 1, 0, List.of(), List.of());
    }
}
