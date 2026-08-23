package dev.mcpcompass.github;

import java.time.Instant;

public record GithubRepositoryMetadata(
        Instant lastActivityAt,
        Instant latestReleaseAt,
        boolean archived,
        String licenseSpdx
) {
}
