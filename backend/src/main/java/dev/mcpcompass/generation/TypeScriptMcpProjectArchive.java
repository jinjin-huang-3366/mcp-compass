package dev.mcpcompass.generation;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
class TypeScriptMcpProjectArchive {
    private static final LocalDateTime ZIP_EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0);
    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,79}");
    private static final Pattern SAFE_PATH = Pattern.compile("(?!/)(?!.*(?:^|/)\\.\\.(?:/|$))[A-Za-z0-9._/-]+");

    Export export(GeneratedTypeScriptProject project) {
        if (project == null || !PROJECT_NAME.matcher(project.projectName()).matches()) {
            throw new IllegalArgumentException("Generated project has an invalid project name.");
        }

        Set<String> paths = new HashSet<>();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (GeneratedTypeScriptProject.File file : project.files()) {
                if (file == null || file.content() == null || !safePath(file.path()) || !paths.add(file.path())) {
                    throw new IllegalArgumentException("Generated project contains an invalid or duplicate file path.");
                }
                ZipEntry entry = new ZipEntry(project.projectName() + "/" + file.path());
                entry.setTimeLocal(ZIP_EPOCH);
                zip.putNextEntry(entry);
                zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot export generated TypeScript project.", error);
        }
        return new Export(project.projectName() + ".zip", output.toByteArray());
    }

    private static boolean safePath(String path) {
        return path != null && !path.isBlank() && !path.contains("\\") && SAFE_PATH.matcher(path).matches();
    }

    record Export(String fileName, byte[] content) {
    }
}
