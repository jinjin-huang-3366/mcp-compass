package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityMetadataNormalizerTest {
    private final CapabilityMetadataNormalizer normalizer = new CapabilityMetadataNormalizer();

    @Test
    void normalizesDeclaredAndToolCapabilitiesWithProvenance() {
        RegistryClient.RegistryServerPayload payload = new RegistryClient.RegistryServerPayload(
                "io.example/github",
                "GitHub",
                "Repository tools",
                "1.0.0",
                "active",
                "{}",
                List.of(
                        new RegistryClient.RegistryToolPayload(
                                "create_pull_request",
                                "Create a pull request",
                                "{\"type\":\"object\"}",
                                List.of(new RegistryClient.RegistryCapabilityPayload(
                                        " GitHub.Pull-Request.Create ",
                                        "Create pull requests"
                                ))
                        ),
                        new RegistryClient.RegistryToolPayload(
                                "list_issues",
                                "List issues",
                                null,
                                List.of()
                        )
                ),
                List.of(
                        new RegistryClient.RegistryCapabilityPayload(
                                " GitHub Issue Read ",
                                "Read issues"
                        ),
                        new RegistryClient.RegistryCapabilityPayload(
                                "github.pull-request.create",
                                null
                        )
                )
        );

        NormalizedCapabilityMetadata result = normalizer.normalize(payload);

        assertThat(result.tools()).extracting(NormalizedCapabilityMetadata.NormalizedTool::name)
                .containsExactly("create_pull_request", "list_issues");
        assertThat(result.tools().getFirst().capabilities()).singleElement().satisfies(capability -> {
            assertThat(capability.canonicalName()).isEqualTo("github.pull-request.create");
            assertThat(capability.confidence()).isEqualTo(1.0);
            assertThat(capability.source()).isEqualTo("tool-metadata");
        });
        assertThat(result.tools().get(1).capabilities()).singleElement().satisfies(capability -> {
            assertThat(capability.canonicalName()).isEqualTo("github.list.issues");
            assertThat(capability.confidence()).isEqualTo(0.7);
            assertThat(capability.source()).isEqualTo("tool-name");
        });
        assertThat(result.serverCapabilities())
                .extracting(NormalizedCapabilityMetadata.NormalizedCapability::canonicalName)
                .containsExactly(
                        "github.issue.read",
                        "github.pull-request.create",
                        "github.list.issues"
                );
        assertThat(result.serverCapabilities().get(1).source()).isEqualTo("server-metadata");
    }

    @Test
    void skipsBlankMetadataAndDuplicateToolNames() {
        RegistryClient.RegistryServerPayload payload = new RegistryClient.RegistryServerPayload(
                "io.example/server",
                "Example",
                "Example",
                "1.0.0",
                "active",
                "{}",
                List.of(
                        new RegistryClient.RegistryToolPayload(" ", null, null, List.of()),
                        new RegistryClient.RegistryToolPayload("search", null, null, List.of()),
                        new RegistryClient.RegistryToolPayload("search", "Duplicate", null, List.of())
                ),
                List.of(new RegistryClient.RegistryCapabilityPayload(" ", null))
        );

        NormalizedCapabilityMetadata result = normalizer.normalize(payload);

        assertThat(result.tools()).singleElement().satisfies(tool ->
                assertThat(tool.name()).isEqualTo("search"));
        assertThat(result.serverCapabilities()).singleElement().satisfies(capability ->
                assertThat(capability.canonicalName()).isEqualTo("server.search"));
    }
}
