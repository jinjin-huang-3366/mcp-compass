package dev.mcpcompass.search;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.RegistryClient;
import dev.mcpcompass.requirement.RequirementConstraint;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEligibilityPolicyTest {
    private final CandidateEligibilityPolicy policy = new CandidateEligibilityPolicy();

    @Test
    void excludesNormalizedForbiddenCapabilityBeforeRanking() {
        StructuredRequirement requirement = requirement(List.of("github.repository.delete"), List.of());

        CandidateEligibilityPolicy.Eligibility result = policy.evaluate(
                requirement,
                server("io.example/github", "GitHub issue reader"),
                Set.of("github.delete_repositories")
        );

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly(
                "forbidden capability advertised: github.repository.delete "
                        + "(normalized capability github.delete_repositories)"
        );
    }

    @Test
    void excludesAdvertisedVoiceAndExplainsMetadataEvidence() {
        StructuredRequirement requirement = requirement(
                List.of("twilio.voice.call.create"),
                List.of(new RequirementConstraint(
                        "communication-channel", RequirementConstraint.Operator.EQUALS, "sms"
                ))
        );

        CandidateEligibilityPolicy.Eligibility result = policy.evaluate(
                requirement,
                server("io.example/twilio", "Send SMS and make voice calls"),
                Set.of()
        );

        assertThat(result.eligible()).isFalse();
        assertThat(result.reasons()).containsExactly(
                "forbidden capability advertised: twilio.voice.call.create (Registry metadata)"
        );
    }

    @Test
    void enforcesReadOnlyConstraintConservatively() {
        StructuredRequirement requirement = requirement(
                List.of("postgres.row.write"),
                List.of(new RequirementConstraint(
                        "access-mode", RequirementConstraint.Operator.EQUALS, "read-only"
                ))
        );

        CandidateEligibilityPolicy.Eligibility readOnly = policy.evaluate(
                requirement,
                server("io.example/postgres-reader", "Read-only PostgreSQL queries; writes refused"),
                Set.of("postgres.query.select")
        );
        CandidateEligibilityPolicy.Eligibility unknown = policy.evaluate(
                requirement,
                server("io.example/postgres", "General PostgreSQL server"),
                Set.of("postgres.query.select")
        );

        assertThat(readOnly.eligible()).isTrue();
        assertThat(unknown.eligible()).isFalse();
        assertThat(unknown.reasons()).containsExactly(
                "hard constraint not evidenced: access-mode EQUALS read-only"
        );
    }

    private static StructuredRequirement requirement(
            List<String> forbidden,
            List<RequirementConstraint> constraints
    ) {
        return new StructuredRequirement("1.0", "", "", List.of(), forbidden, constraints);
    }

    private static McpServerEntity server(String registryName, String description) {
        Instant recordedAt = Instant.parse("2026-08-31T00:00:00Z");
        McpServerEntity server = McpServerEntity.create(registryName, recordedAt);
        server.updateFrom(new RegistryClient.RegistryServerPayload(
                registryName,
                registryName,
                description,
                "1.0.0",
                "active",
                "{}",
                true,
                null,
                1,
                0,
                List.of(),
                List.of()
        ), recordedAt);
        return server;
    }
}
