package dev.mcpcompass.requirement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StructuredRequirementTest {
    @Test
    void representsVersionedRequirementIntent() {
        StructuredRequirement requirement = new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                " source-control ",
                " GitHub ",
                List.of("github.issue.read", "github.pull-request.create"),
                List.of("github.repository.delete"),
                List.of(new RequirementConstraint(
                        "authentication",
                        RequirementConstraint.Operator.EQUALS,
                        "oauth2"
                ))
        );

        assertThat(requirement.schemaVersion()).isEqualTo("1.0");
        assertThat(requirement.domain()).isEqualTo("source-control");
        assertThat(requirement.service()).isEqualTo("GitHub");
        assertThat(requirement.requiredCapabilities())
                .containsExactly("github.issue.read", "github.pull-request.create");
        assertThat(requirement.forbiddenCapabilities()).containsExactly("github.repository.delete");
        assertThat(requirement.constraints()).containsExactly(
                new RequirementConstraint("authentication", RequirementConstraint.Operator.EQUALS, "oauth2")
        );
    }

    @Test
    void takesImmutableCopiesOfCollections() {
        List<String> requiredCapabilities = new ArrayList<>(List.of("github.issue.read"));
        List<RequirementConstraint> constraints = new ArrayList<>();

        StructuredRequirement requirement = new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                "source-control",
                "github",
                requiredCapabilities,
                List.of(),
                constraints
        );

        requiredCapabilities.add("github.issue.write");
        constraints.add(new RequirementConstraint("deployment", RequirementConstraint.Operator.EQUALS, "local"));

        assertThat(requirement.requiredCapabilities()).containsExactly("github.issue.read");
        assertThat(requirement.constraints()).isEmpty();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> requirement.requiredCapabilities().add("github.issue.write"));
    }

    @Test
    void rejectsUnsupportedVersionsAndConflictingCapabilities() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StructuredRequirement(
                "2.0",
                "source-control",
                "github",
                List.of(),
                List.of(),
                List.of()
        )).withMessageContaining("Unsupported");

        assertThatIllegalArgumentException().isThrownBy(() -> new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                "source-control",
                "github",
                List.of("github.issue.write"),
                List.of("github.issue.write"),
                List.of()
        )).withMessageContaining("both required and forbidden");
    }

    @Test
    void rejectsIncompleteStructuredValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StructuredRequirement(
                StructuredRequirement.CURRENT_SCHEMA_VERSION,
                "source-control",
                "github",
                List.of(" "),
                List.of(),
                List.of()
        )).withMessageContaining("blank");

        assertThatIllegalArgumentException().isThrownBy(() ->
                new RequirementConstraint("authentication", RequirementConstraint.Operator.EQUALS, " ")
        ).withMessageContaining("value must not be blank");
    }
}
