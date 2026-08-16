package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrySyncStateEntityTest {
    private static final Instant STARTED = Instant.parse("2026-08-15T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-08-15T10:00:05Z");

    @Test
    void advancesUpdatedSinceOnlyWhenPaginationCompletes() {
        RegistrySyncStateEntity state = new RegistrySyncStateEntity("source");

        state.pageSucceeded("next", STARTED, FINISHED);
        assertThat(state.nextCursor()).isEqualTo("next");
        assertThat(state.updatedSince()).isNull();

        state.pageSucceeded(null, STARTED, FINISHED);
        assertThat(state.nextCursor()).isNull();
        assertThat(state.updatedSince()).isEqualTo(STARTED);
    }
}
