package dev.mcpcompass.remote;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RemoteConnectionCheckService {
    private final McpServerRepository repository;
    private final RemoteConnectionSource source;
    private final RemoteConnectionProbe probe;
    private final Clock clock;
    private final Set<String> allowedHosts;
    private final Set<String> optedOutNames;

    @Autowired
    public RemoteConnectionCheckService(McpServerRepository repository, RemoteConnectionSource source,
            RemoteConnectionProbe probe,
            @Value("${app.remote-check.allowed-hosts:}") String allowedHosts,
            @Value("${app.remote-check.opt-out-names:}") String optedOutNames) {
        this(repository, source, probe, Clock.systemUTC(), csv(allowedHosts), csv(optedOutNames));
    }

    RemoteConnectionCheckService(McpServerRepository repository, RemoteConnectionSource source,
            RemoteConnectionProbe probe, Clock clock, Set<String> allowedHosts, Set<String> optedOutNames) {
        this.repository = repository;
        this.source = source;
        this.probe = probe;
        this.clock = clock;
        this.allowedHosts = allowedHosts;
        this.optedOutNames = optedOutNames;
    }

    /** At most ten checks per invocation and one request per second. Search never invokes this. */
    public int checkDue() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(24));
        var due = repository.findRemoteChecksDue(cutoff, PageRequest.of(0, 10));
        int requests = 0;
        for (McpServerEntity server : due) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            if (optedOutNames.contains(server.getRegistryName().toLowerCase(Locale.ROOT))) {
                save(server, RemoteConnectionState.NOT_CHECKED, "Remote check opted out by configuration");
                continue;
            }
            URI endpoint = source.endpoint(server).orElse(null);
            if (endpoint == null) {
                save(server, RemoteConnectionState.NOT_CHECKED, "No supported streamable HTTP endpoint");
                continue;
            }
            if (!permitted(endpoint)) {
                save(server, RemoteConnectionState.NOT_CHECKED, "Endpoint host is not allowlisted or URL is invalid");
                continue;
            }
            if (requests > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            RemoteConnectionProbe.Result result = probe.check(endpoint);
            save(server, result.state(), result.reason());
            requests++;
        }
        return requests;
    }

    private boolean permitted(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && uri.getUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private void save(McpServerEntity server, RemoteConnectionState state, String reason) {
        server.recordRemoteConnection(state.name(), clock.instant(), reason);
        repository.save(server);
    }

    private static Set<String> csv(String input) {
        return Arrays.stream(input.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
