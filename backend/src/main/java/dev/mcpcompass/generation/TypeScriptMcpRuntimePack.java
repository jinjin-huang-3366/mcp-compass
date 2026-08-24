package dev.mcpcompass.generation;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class TypeScriptMcpRuntimePack {
    private static final String RESOURCE_ROOT = "generator/typescript/v1/";
    private static final List<String> RESOURCE_PATHS = List.of(
            "package.json",
            "package-lock.json",
            "tsconfig.json",
            ".env.example",
            "README.md",
            "src/api-client.ts",
            "src/api-client.test.ts",
            "src/index.ts"
    );

    private final Map<String, String> contents;

    TypeScriptMcpRuntimePack() {
        contents = RESOURCE_PATHS.stream().collect(Collectors.toUnmodifiableMap(
                Function.identity(), TypeScriptMcpRuntimePack::readResource
        ));
    }

    String content(String path) {
        String content = contents.get(path);
        if (content == null) {
            throw new IllegalArgumentException("Unknown TypeScript runtime-pack file: " + path);
        }
        return content;
    }

    GeneratedTypeScriptProject.File file(String path) {
        String content = content(path);
        return new GeneratedTypeScriptProject.File(path, content.endsWith("\n") ? content : content + "\n");
    }

    private static String readResource(String path) {
        String resourcePath = RESOURCE_ROOT + path;
        try (InputStream input = TypeScriptMcpRuntimePack.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing TypeScript runtime-pack resource: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read TypeScript runtime-pack resource: " + resourcePath, error);
        }
    }
}
