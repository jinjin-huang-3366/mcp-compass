package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeScriptMcpProjectArchiveTest {
    private final TypeScriptMcpProjectArchive archive = new TypeScriptMcpProjectArchive();

    @Test
    void createsDeterministicArchive() {
        GeneratedTypeScriptProject project = new GeneratedTypeScriptProject(
                "1.0", "sample-mcp-server", "typescript", "1.0",
                List.of(new GeneratedTypeScriptProject.File("README.md", "hello\n")));

        TypeScriptMcpProjectArchive.Export first = archive.export(project);
        TypeScriptMcpProjectArchive.Export second = archive.export(project);

        assertThat(first.fileName()).isEqualTo("sample-mcp-server.zip");
        assertThat(first.content()).isEqualTo(second.content());
    }

    @Test
    void rejectsArchiveTraversalPath() {
        GeneratedTypeScriptProject project = new GeneratedTypeScriptProject(
                "1.0", "sample-mcp-server", "typescript", "1.0",
                List.of(new GeneratedTypeScriptProject.File("../outside.txt", "unsafe")));

        assertThatThrownBy(() -> archive.export(project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("file path");
    }
}
