package dev.mcpcompass.validationworker;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratedProjectWorkspaceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void materializesSnapshotUnderAUniqueWorkspaceAndDeletesItAfterUse() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "workspace-test-materialize")).toAbsolutePath();
        String manifest = """
                {"files":[
                  {"path":"package.json","content":"{}"},
                  {"path":"src/index.ts","content":"console.log('ready')"}
                ]}
                """;
        Path directory;

        try (GeneratedProjectWorkspace workspace = GeneratedProjectWorkspace.materialize(root, manifest, objectMapper)) {
            directory = workspace.directory();
            assertThat(Files.readString(directory.resolve("src/index.ts"))).isEqualTo("console.log('ready')");
        }

        assertThat(directory).doesNotExist();
    }

    @Test
    void rejectsTraversalBeforeWritingOutsideTheWorkspace() throws Exception {
        Path root = Files.createDirectories(Path.of("target", "workspace-test-traversal")).toAbsolutePath();
        String manifest = """
                {"files":[{"path":"../escaped.txt","content":"untrusted"}]}
                """;

        assertThatThrownBy(() -> GeneratedProjectWorkspace.materialize(root, manifest, objectMapper))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes the workspace");
        assertThat(root.resolve("escaped.txt")).doesNotExist();
    }
}
