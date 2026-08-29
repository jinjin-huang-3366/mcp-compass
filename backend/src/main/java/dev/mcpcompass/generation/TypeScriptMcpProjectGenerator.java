package dev.mcpcompass.generation;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class TypeScriptMcpProjectGenerator implements GeneratedProjectProvider {
    private static final String GENERATOR_VERSION = "1.0";
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern HTTP_METHOD = Pattern.compile("GET|PUT|POST|DELETE|OPTIONS|HEAD|PATCH|TRACE");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private static final int MAX_TOOLS = 100;

    private final ObjectMapper objectMapper;
    private final TypeScriptMcpRuntimePack runtimePack;

    TypeScriptMcpProjectGenerator(ObjectMapper objectMapper, TypeScriptMcpRuntimePack runtimePack) {
        this.objectMapper = objectMapper;
        this.runtimePack = runtimePack;
    }

    @Override
    public GeneratedTypeScriptProject generate(McpToolContract contract) {
        validate(contract);
        String projectName = projectName(contract.source().title());
        List<GeneratedTypeScriptProject.File> files = List.of(
                file("package.json", packageJson(projectName)),
                file("package-lock.json", packageLockJson(projectName)),
                runtimePack.file("tsconfig.json"),
                runtimePack.file(".gitignore"),
                runtimePack.file(".env.example"),
                runtimePack.file("README.md"),
                runtimePack.file(".github/workflows/ci.yml"),
                file("contract.json", prettyJson(contract)),
                runtimePack.file("src/api-client.ts"),
                runtimePack.file("src/api-client.test.ts"),
                runtimePack.file("src/index.ts")
        );
        return new GeneratedTypeScriptProject(
                GENERATOR_VERSION, projectName, "typescript", contract.contractVersion(), files
        );
    }

    private void validate(McpToolContract contract) {
        if (contract == null || !"1.0".equals(contract.contractVersion()) || !"APPROVED".equals(contract.status())) {
            throw invalid("Only a version 1.0 APPROVED contract can be generated.");
        }
        if (contract.source() == null || blank(contract.source().title()) || blank(contract.source().apiVersion())) {
            throw invalid("The approved contract must identify its source title and API version.");
        }
        if (contract.tools() == null || contract.tools().isEmpty() || contract.tools().size() > MAX_TOOLS) {
            throw invalid("The approved contract must contain 1 to 100 tools.");
        }

        Set<String> names = new HashSet<>();
        for (McpToolContract.Tool tool : contract.tools()) {
            if (tool == null || !TOOL_NAME.matcher(value(tool.name())).matches() || !names.add(tool.name())) {
                throw invalid("Approved tool names must be valid and unique.");
            }
            if (blank(tool.description()) || tool.description().length() > 500
                    || tool.sourceOperation() == null || tool.risk() == null) {
                throw invalid("Every approved tool needs a description, source operation, and risk.");
            }
            McpToolContract.Operation operation = tool.sourceOperation();
            if (!HTTP_METHOD.matcher(value(operation.method())).matches()
                    || blank(operation.path()) || !operation.path().startsWith("/")
                    || operation.path().contains("\r") || operation.path().contains("\n")) {
                throw invalid("Every source operation needs a supported uppercase HTTP method and absolute path.");
            }
            if (!objectSchema(tool.inputSchema()) || !declaredSchema(tool.outputSchema())) {
                throw invalid("Every approved tool needs object input and declared output JSON schemas.");
            }
            var matcher = PATH_PARAMETER.matcher(operation.path());
            while (matcher.find()) {
                if (!tool.inputSchema().path("properties").has(matcher.group(1))) {
                    throw invalid("Every path parameter must be declared in the tool input schema.");
                }
            }
            if (tool.authenticationRequirements() == null
                    || tool.authenticationRequirements().stream().anyMatch(TypeScriptMcpProjectGenerator::blank)) {
                throw invalid("Authentication requirement names cannot be blank.");
            }
        }
    }

    private static boolean objectSchema(JsonNode schema) {
        return declaredSchema(schema) && "object".equals(schema.path("type").stringValue());
    }

    private static boolean declaredSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return false;
        }
        if (schema.path("type").isString()) {
            return true;
        }
        return List.of("allOf", "anyOf", "oneOf").stream()
                .map(schema::path)
                .anyMatch(composition -> composition.isArray() && !composition.isEmpty());
    }

    private String packageJson(String projectName) {
        ObjectNode root = jsonObject("package.json");
        root.put("name", projectName);
        return prettyJson(root);
    }

    private String packageLockJson(String projectName) {
        ObjectNode root = jsonObject("package-lock.json");
        root.put("name", projectName);
        JsonNode rootPackage = root.path("packages").path("");
        if (!(rootPackage instanceof ObjectNode packageMetadata)) {
            throw new IllegalStateException("TypeScript runtime pack package-lock.json must describe the root package.");
        }
        packageMetadata.put("name", projectName);
        return prettyJson(root);
    }

    private ObjectNode jsonObject(String path) {
        JsonNode template = objectMapper.readTree(runtimePack.content(path));
        if (!(template instanceof ObjectNode root)) {
            throw new IllegalStateException("TypeScript runtime pack " + path + " must contain a JSON object.");
        }
        return root;
    }

    private static String projectName(String title) {
        String slug = title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "generated-api";
        }
        return slug.substring(0, Math.min(slug.length(), 68)) + "-mcp-server";
    }

    private GeneratedTypeScriptProject.File file(String path, String content) {
        return new GeneratedTypeScriptProject.File(path, content.endsWith("\n") ? content : content + "\n");
    }

    private String prettyJson(Object value) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private static OpenApiSourceException invalid(String message) {
        return new OpenApiSourceException("INVALID_APPROVED_CONTRACT", message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
