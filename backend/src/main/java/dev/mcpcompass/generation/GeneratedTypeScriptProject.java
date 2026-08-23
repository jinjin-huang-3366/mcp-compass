package dev.mcpcompass.generation;

import java.util.List;

public record GeneratedTypeScriptProject(
        String generatorVersion,
        String projectName,
        String language,
        String contractVersion,
        List<File> files
) {
    public GeneratedTypeScriptProject {
        files = List.copyOf(files);
    }

    public record File(String path, String content) {
    }
}
