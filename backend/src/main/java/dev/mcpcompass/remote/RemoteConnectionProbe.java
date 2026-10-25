package dev.mcpcompass.remote;

import java.net.URI;

public interface RemoteConnectionProbe {
    Result check(URI endpoint);

    record Result(RemoteConnectionState state, String reason) {
    }
}
