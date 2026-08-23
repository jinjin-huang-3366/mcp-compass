package dev.mcpcompass.generation;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
class TypeScriptMcpProjectGenerator {
    private static final String GENERATOR_VERSION = "1.0";
    private static final Pattern TOOL_NAME = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern HTTP_METHOD = Pattern.compile("GET|PUT|POST|DELETE|OPTIONS|HEAD|PATCH|TRACE");
    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");
    private static final int MAX_TOOLS = 100;

    private final ObjectMapper objectMapper;

    TypeScriptMcpProjectGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    GeneratedTypeScriptProject generate(McpToolContract contract) {
        validate(contract);
        String projectName = projectName(contract.source().title());
        List<GeneratedTypeScriptProject.File> files = List.of(
                file("package.json", packageJson(projectName)),
                file("tsconfig.json", tsconfig()),
                file(".env.example", envExample(contract)),
                file("README.md", readme(projectName, contract)),
                file("contract.json", prettyJson(contract)),
                file("src/api-client.ts", apiClient()),
                file("src/index.ts", serverSource(projectName, contract))
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
        return schema != null && schema.isObject() && schema.path("type").isString();
    }

    private String packageJson(String projectName) {
        var root = objectMapper.createObjectNode()
                .put("name", projectName)
                .put("version", "0.1.0")
                .put("private", true)
                .put("type", "module");
        root.putObject("scripts")
                .put("build", "tsc")
                .put("start", "node build/index.js");
        root.putObject("dependencies")
                .put("@modelcontextprotocol/server", "^2.0.0");
        root.putObject("devDependencies")
                .put("@types/node", "^24.3.0")
                .put("typescript", "^5.9.3");
        return prettyJson(root);
    }

    private static String tsconfig() {
        return """
                {
                  "compilerOptions": {
                    "target": "ES2022",
                    "module": "NodeNext",
                    "moduleResolution": "NodeNext",
                    "outDir": "build",
                    "rootDir": "src",
                    "strict": true,
                    "types": ["node"],
                    "skipLibCheck": true
                  },
                  "include": ["src/**/*.ts"]
                }
                """;
    }

    private static String envExample(McpToolContract contract) {
        boolean authenticated = contract.tools().stream()
                .anyMatch(tool -> !tool.authenticationRequirements().isEmpty());
        return "API_BASE_URL=https://api.example.com\n"
                + (authenticated ? "API_AUTH_TOKEN=replace-me\n" : "");
    }

    private static String readme(String projectName, McpToolContract contract) {
        String auth = contract.tools().stream().anyMatch(tool -> !tool.authenticationRequirements().isEmpty())
                ? " Set `API_AUTH_TOKEN` for the declared authentication schemes; review the generated bearer-token mapping for your API."
                : "";
        return """
                # %s

                Generated from an approved MCP Compass tool contract. The contract is preserved in `contract.json`.

                ## Configure

                Copy `.env.example` into your secret manager or runtime environment and set `API_BASE_URL`.%s Never commit real credentials.

                ## Build and run

                ```bash
                npm install
                npm run build
                npm start
                ```

                The server uses stdio, so stdout is reserved for MCP protocol messages. Review and compile the generated project before running it. MCP Compass does not execute this project during generation.
                """.formatted(projectName, auth);
    }

    private static String apiClient() {
        return """
                export type ApiOperation = {
                  method: string;
                  path: string;
                  authenticationRequired: boolean;
                };

                function requiredEnvironment(name: string): string {
                  const value = process.env[name]?.trim();
                  if (!value) throw new Error(`Missing required environment variable ${name}`);
                  return value;
                }

                function queryValue(value: unknown): string {
                  return typeof value === "string" ? value : JSON.stringify(value);
                }

                export async function callApi(operation: ApiOperation, input: Record<string, unknown>): Promise<unknown> {
                  const baseUrl = requiredEnvironment("API_BASE_URL");
                  const remaining = { ...input };
                  const resolvedPath = operation.path.replace(/\\{([^}]+)}/g, (_match, name: string) => {
                    const value = remaining[name];
                    if (value === undefined || value === null) throw new Error(`Missing path parameter ${name}`);
                    delete remaining[name];
                    return encodeURIComponent(String(value));
                  });
                  const body = remaining.body;
                  delete remaining.body;
                  const url = new URL(resolvedPath, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`);
                  for (const [name, value] of Object.entries(remaining)) {
                    if (value !== undefined && value !== null) url.searchParams.set(name, queryValue(value));
                  }

                  const headers: Record<string, string> = { Accept: "application/json" };
                  if (operation.authenticationRequired) {
                    headers.Authorization = `Bearer ${requiredEnvironment("API_AUTH_TOKEN")}`;
                  }
                  if (body !== undefined) headers["Content-Type"] = "application/json";

                  const response = await fetch(url, {
                    method: operation.method,
                    headers,
                    body: body === undefined ? undefined : JSON.stringify(body),
                  });
                  const text = await response.text();
                  const payload: unknown = text ? parseResponse(text, response.headers.get("content-type")) : null;
                  if (!response.ok) throw new Error(`Upstream API returned ${response.status}: ${text.slice(0, 500)}`);
                  return payload;
                }

                function parseResponse(text: string, contentType: string | null): unknown {
                  if (contentType?.includes("json")) return JSON.parse(text);
                  return text;
                }
                """;
    }

    private String serverSource(String projectName, McpToolContract contract) {
        StringBuilder registrations = new StringBuilder();
        for (McpToolContract.Tool tool : contract.tools()) {
            McpToolContract.Operation operation = tool.sourceOperation();
            registrations.append("""

                    server.registerTool(%s, {
                      description: %s,
                      inputSchema: fromJsonSchema(JSON.parse(%s)),
                      outputSchema: fromJsonSchema(JSON.parse(%s)),
                      annotations: {
                        readOnlyHint: %s,
                        destructiveHint: %s,
                        idempotentHint: %s,
                        openWorldHint: true,
                      },
                    }, async (input) => {
                      try {
                        const result = await callApi({ method: %s, path: %s, authenticationRequired: %s }, input as Record<string, unknown>);
                        return { content: [{ type: "text", text: JSON.stringify(result) }] };
                      } catch (error) {
                        const message = error instanceof Error ? error.message : "Unknown API error";
                        return { content: [{ type: "text", text: message }], isError: true };
                      }
                    });
                    """.formatted(
                    jsonString(tool.name()), jsonString(tool.description()),
                    jsonString(compactJson(tool.inputSchema())), jsonString(compactJson(tool.outputSchema())),
                    tool.risk() == McpToolContract.Risk.READ_ONLY,
                    tool.risk() == McpToolContract.Risk.DESTRUCTIVE,
                    Set.of("GET", "PUT", "DELETE", "HEAD", "OPTIONS").contains(operation.method()),
                    jsonString(operation.method()), jsonString(operation.path()),
                    !tool.authenticationRequirements().isEmpty()
            ));
        }
        return """
                import { McpServer, fromJsonSchema } from "@modelcontextprotocol/server";
                import { serveStdio } from "@modelcontextprotocol/server/stdio";
                import { callApi } from "./api-client.js";

                function createServer(): McpServer {
                  const server = new McpServer({ name: %s, version: "0.1.0" });
                %s
                  return server;
                }

                void serveStdio(createServer);
                console.error(%s);
                """.formatted(jsonString(projectName), registrations, jsonString(projectName + " running on stdio"));
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

    private String jsonString(String value) {
        return compactJson(value);
    }

    private String prettyJson(Object value) {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    }

    private String compactJson(Object value) {
        return objectMapper.writeValueAsString(value);
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
