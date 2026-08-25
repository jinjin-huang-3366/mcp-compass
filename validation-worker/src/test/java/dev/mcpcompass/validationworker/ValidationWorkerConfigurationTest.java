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
        assertThat(configuration.pollInterval()).hasSeconds(5);
    }

    @Test
    void rejectsUnboundedStartupWindow() {
        assertThatThrownBy(() -> ValidationWorkerConfiguration.from(Map.of(
                "VALIDATION_STARTUP_WINDOW_SECONDS", "301"
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("VALIDATION_STARTUP_WINDOW_SECONDS");
    }
}
