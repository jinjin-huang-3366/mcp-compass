package dev.mcpcompass.registry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
class RegistrySyncMetrics {
    static final String PAGES = "mcp.registry.sync.pages";
    static final String ITEMS = "mcp.registry.sync.items";
    static final String ERRORS = "mcp.registry.sync.errors";
    static final String LAST_SUCCESS = "mcp.registry.sync.last.success";
    static final String SOURCE_TAG = "source";

    private final Counter pages;
    private final Counter items;
    private final Counter errors;
    private final AtomicLong lastSuccessEpochSeconds = new AtomicLong();

    RegistrySyncMetrics(MeterRegistry registry) {
        pages = counter(registry, PAGES, "Registry pages persisted successfully");
        items = counter(registry, ITEMS, "Registry items persisted successfully");
        errors = counter(registry, ERRORS, "Registry sync failures");
        Gauge.builder(LAST_SUCCESS, lastSuccessEpochSeconds, AtomicLong::doubleValue)
                .description("Unix timestamp of the last fully completed Registry sync")
                .baseUnit("seconds")
                .tag(SOURCE_TAG, RegistrySyncStore.SOURCE)
                .register(registry);
    }

    void recordPage(int itemCount) {
        pages.increment();
        items.increment(itemCount);
    }

    void recordError() {
        errors.increment();
    }

    void restoreLastSuccess(Instant lastSuccessAt) {
        updateLastSuccess(lastSuccessAt);
    }

    void recordCompletedSync(Instant completedAt) {
        updateLastSuccess(completedAt);
    }

    private void updateLastSuccess(Instant timestamp) {
        if (timestamp != null) {
            lastSuccessEpochSeconds.accumulateAndGet(timestamp.getEpochSecond(), Math::max);
        }
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name)
                .description(description)
                .tag(SOURCE_TAG, RegistrySyncStore.SOURCE)
                .register(registry);
    }
}
