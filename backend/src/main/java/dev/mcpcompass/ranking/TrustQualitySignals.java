package dev.mcpcompass.ranking;

import java.time.Instant;

public record TrustQualitySignals(
        Boolean repositoryArchived,
        String licenseSpdx,
        Instant lastActivityAt,
        Instant collectedAt
) {
    public static TrustQualitySignals unavailable() {
        return new TrustQualitySignals(null, null, null, null);
    }
}
