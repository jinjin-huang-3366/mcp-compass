package dev.mcpcompass.capability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityNameNormalizerTest {
    @Test
    void canonicalizesCaseWhitespaceAndSeparators() {
        assertThat(CapabilityNameNormalizer.canonicalName(" GitHub / Pull-Request : Create "))
                .isEqualTo("github.pull-request.create");
    }

    @Test
    void givesActionFirstToolNamesAndRequirementCapabilitiesTheSameMatchingKey() {
        assertThat(CapabilityNameNormalizer.matchingKey("github.create_pull_requests"))
                .isEqualTo("github.pull.request.create")
                .isEqualTo(CapabilityNameNormalizer.matchingKey("github.pull-request.create"));
        assertThat(CapabilityNameNormalizer.matchingKey("google-calendar.list_events"))
                .isEqualTo(CapabilityNameNormalizer.matchingKey("google-calendar.event.list"));
        assertThat(CapabilityNameNormalizer.matchingKey("github-actions.rerun_jobs"))
                .isEqualTo(CapabilityNameNormalizer.matchingKey("github-actions.job.rerun"));
    }

    @Test
    void normalizesPluralResourcesWithoutDamagingDoubleSWords() {
        assertThat(CapabilityNameNormalizer.matchingKey("github.list_issues"))
                .isEqualTo("github.issue.list");
        assertThat(CapabilityNameNormalizer.matchingKey("css.read"))
                .isEqualTo("css.read");
    }
}
