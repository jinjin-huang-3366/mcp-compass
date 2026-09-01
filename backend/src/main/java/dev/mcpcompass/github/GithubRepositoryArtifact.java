package dev.mcpcompass.github;

import java.util.List;

record GithubRepositoryArtifact(
        Kind kind,
        String sourcePath,
        String sourceUrl,
        String sourceRevision,
        String mediaType,
        String content,
        String contentSha256,
        List<StaticTool> tools
) {
    GithubRepositoryArtifact {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }

    enum Kind { README, STATIC_TOOL_METADATA }

    record StaticTool(String name, String description, String inputSchema) {
    }
}
