package dev.mcpcompass.generation;

import java.net.URI;

interface OpenApiUrlFetcher {
    byte[] fetch(URI uri);
}
