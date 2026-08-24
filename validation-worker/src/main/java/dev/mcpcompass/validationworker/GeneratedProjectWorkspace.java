package dev.mcpcompass.validationworker;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class GeneratedProjectWorkspace implements AutoCloseable {
    private static final int MAX_FILES = 100;
    private static final long MAX_TOTAL_BYTES = 10 * 1024 * 1024;

    private final Path directory;

    private GeneratedProjectWorkspace(Path directory) {
        this.directory = directory;
    }

    static GeneratedProjectWorkspace materialize(
            Path workspaceRoot,
            String manifest,
            ObjectMapper objectMapper
    ) throws IOException {
        ProjectSnapshot snapshot = objectMapper.readValue(manifest, ProjectSnapshot.class);
        if (snapshot.files() == null || snapshot.files().isEmpty() || snapshot.files().size() > MAX_FILES) {
            throw new IllegalArgumentException("Generated project snapshot must contain 1 to 100 files");
        }

        Files.createDirectories(workspaceRoot);
        Path directory = Files.createTempDirectory(workspaceRoot, "job-").toAbsolutePath().normalize();
        GeneratedProjectWorkspace workspace = new GeneratedProjectWorkspace(directory);
        try {
            long totalBytes = 0;
            Set<Path> paths = new HashSet<>();
            for (ProjectFile file : snapshot.files()) {
                if (file == null || file.path() == null || file.path().isBlank() || file.content() == null) {
                    throw new IllegalArgumentException("Generated project files require a path and content");
                }
                Path relative = Path.of(file.path()).normalize();
                if (relative.isAbsolute() || relative.startsWith("..") || relative.toString().isBlank()) {
                    throw new IllegalArgumentException("Generated project path escapes the workspace: " + file.path());
                }
                Path target = directory.resolve(relative).normalize();
                if (!target.startsWith(directory) || !paths.add(target)) {
                    throw new IllegalArgumentException("Generated project path is unsafe or duplicated: " + file.path());
                }
                totalBytes += file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Generated project snapshot exceeds 10 MiB");
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.content());
            }
            return workspace;
        } catch (RuntimeException | IOException error) {
            workspace.close();
            throw error;
        }
    }

    Path directory() {
        return directory;
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record ProjectSnapshot(List<ProjectFile> files) {
    }

    private record ProjectFile(String path, String content) {
    }
}
