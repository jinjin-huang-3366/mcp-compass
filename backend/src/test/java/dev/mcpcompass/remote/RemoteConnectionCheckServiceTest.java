package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RemoteConnectionCheckServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");

    @Test
    void persistsGatedStateAndSkipsOptOutAndUnlistedHosts() {
        McpServerRepository repository = mock(McpServerRepository.class);
        RemoteConnectionSource source = mock(RemoteConnectionSource.class);
        RemoteConnectionProbe probe = mock(RemoteConnectionProbe.class);
        var gated = server("example/gated");
        var optedOut = server("example/opted-out");
        var unlisted = server("example/unlisted");
        when(repository.findRemoteChecksDue(any(), any(Pageable.class))).thenReturn(List.of(gated, optedOut, unlisted));
        when(source.endpoint(gated)).thenReturn(Optional.of(URI.create("https://remote.example/mcp")));
        when(source.endpoint(unlisted)).thenReturn(Optional.of(URI.create("https://other.example/mcp")));
        when(probe.check(URI.create("https://remote.example/mcp")))
                .thenReturn(new RemoteConnectionProbe.Result(RemoteConnectionState.AUTH_REQUIRED, "HTTP 401 requires authorization"));
        var service = new RemoteConnectionCheckService(repository, source, probe,
                Clock.fixed(NOW, ZoneOffset.UTC), Set.of("remote.example"), Set.of("example/opted-out"));

        assertThat(service.checkDue()).isEqualTo(1);
        assertThat(gated.getRemoteConnectionState()).isEqualTo("AUTH_REQUIRED");
        assertThat(gated.getRemoteConnectionCheckedAt()).isEqualTo(NOW);
        assertThat(gated.getRemoteConnectionReason()).contains("401");
        assertThat(optedOut.getRemoteConnectionState()).isEqualTo("NOT_CHECKED");
        assertThat(unlisted.getRemoteConnectionState()).isEqualTo("NOT_CHECKED");
        verify(probe, times(1)).check(any());
    }

    @Test
    void changedRegistryDeclarationInvalidatesPriorObservation() {
        var server = server("example/changed");
        server.recordRemoteConnection("REACHABLE", NOW, "MCP initialize succeeded");
        server.updateFrom(new RegistryClient.RegistryServerPayload("example/changed", "changed", "remote", "2",
                "active", "{\"server\":{\"remotes\":[]}}", true, null, 0, 1, List.of(), List.of()), NOW);
        assertThat(server.getRemoteConnectionState()).isEqualTo("NOT_CHECKED");
        assertThat(server.getRemoteConnectionCheckedAt()).isNull();
    }

    private static McpServerEntity server(String name) {
        var server = McpServerEntity.create(name, NOW);
        server.updateFrom(new RegistryClient.RegistryServerPayload(name, name, "remote", "1", "active",
                "{}", true, null, 0, 1, List.of(), List.of()), NOW);
        return server;
    }
}
