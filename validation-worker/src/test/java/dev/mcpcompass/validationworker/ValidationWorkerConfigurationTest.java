package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationWorkerConfigurationTest {
    @Test
    void usesSafeBoundedDefaults() {
        ValidationWorkerConfiguration configuration = ValidationWorkerConfiguration.from(Map.of());

        assertThat(configuration.generatedImage()).isEqualTo("mcp-compass/typescript-sandbox:1.0");
        assertThat(configuration.startupWindow()).hasSeconds(5);
        assertThat(configuration.protocolTimeout()).hasSeconds(30);
        assertThat(configuration.pollInterval()).hasSeconds(5);
        assertThat(configuration.sandboxPolicy()).satisfies(policy -> {
            assertThat(policy.user()).isEqualTo("65532:65532");
            assertThat(policy.cpuLimit()).isEqualTo("0.5");
            assertThat(policy.memoryLimit()).isEqualTo("256m");
            assertThat(policy.processLimit()).isEqualTo(64);
            assertThat(policy.wallTimeLimit()).hasSeconds(30);
            assertThat(policy.network()).isEqualTo("none");
        });
    }

    @Test
    void rejectsUnboundedStartupWindow() {
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_STARTUP_WINDOW_SECONDS", "301"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VALIDATION_STARTUP_WINDOW_SECONDS");
    }

    @Test
    void rejectsUnboundedProtocolTimeout() {
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_PROTOCOL_TIMEOUT_SECONDS", "301"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VALIDATION_PROTOCOL_TIMEOUT_SECONDS");
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_PROTOCOL_TIMEOUT_SECONDS", "31",
                "VALIDATION_WALL_TIME_LIMIT_SECONDS", "30"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed VALIDATION_WALL_TIME_LIMIT_SECONDS");
    }

    @Test
    void acceptsOnlyAnExplicitlyAllowListedCustomNetwork() {
        ValidationWorkerConfiguration configuration = ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_NETWORK", "validation-egress-api",
                "VALIDATION_ALLOWED_NETWORKS", "validation-egress-docs, validation-egress-api"
        ));

        assertThat(configuration.sandboxPolicy().network()).isEqualTo("validation-egress-api");

        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_NETWORK", "unreviewed-network",
                "VALIDATION_ALLOWED_NETWORKS", "validation-egress-api"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VALIDATION_ALLOWED_NETWORKS");
    }

    @Test
    void rejectsRootAndUnsafeOrUnboundedResourceSettings() {
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_CONTAINER_USER", "0:0"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-zero numeric uid:gid");
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_MEMORY_LIMIT_MB", "8192"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Memory limit");
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_WALL_TIME_LIMIT_SECONDS", "4"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_NETWORK", "host",
                "VALIDATION_ALLOWED_NETWORKS", "host"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Built-in shared Docker networks");
    }
}
