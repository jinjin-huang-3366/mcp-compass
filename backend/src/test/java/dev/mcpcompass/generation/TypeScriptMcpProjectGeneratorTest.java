package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeScriptMcpProjectGeneratorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeScriptMcpProjectGenerator generator = new TypeScriptMcpProjectGenerator(objectMapper);

    @Test
    void generatesDeterministicTypeScriptProjectFromApprovedContract() {
        GeneratedTypeScriptProject project = generator.generate(approvedContract());

        assertThat(project.projectName()).isEqualTo("pet-store-mcp-server");
        assertThat(project.language()).isEqualTo("typescript");
        assertThat(project.contractVersion()).isEqualTo("1.0");
        assertThat(project.files()).extracting(GeneratedTypeScriptProject.File::path)
                .containsExactly("package.json", "tsconfig.json", ".env.example", "README.md",
                        "contract.json", "src/api-client.ts", "src/index.ts");

        Map<String, String> files = project.files().stream().collect(Collectors.toMap(
                GeneratedTypeScriptProject.File::path, GeneratedTypeScriptProject.File::content));
        assertThat(files.get("package.json"))
                .contains("\"@modelcontextprotocol/server\" : \"^2.0.0\"")
                .contains("\"build\" : \"tsc\"");
        assertThat(files.get(".env.example"))
                .contains("API_BASE_URL=https://api.example.com")
                .contains("API_AUTH_TOKEN=replace-me");
        assertThat(files.get("src/index.ts"))
                .contains("server.registerTool(\"find_pets\"")
                .contains("fromJsonSchema(JSON.parse")
                .contains("method: \"GET\", path: \"/pets/{petId}\", authenticationRequired: true")
                .contains("readOnlyHint: true")
                .contains("destructiveHint: false");
        assertThat(files.get("src/api-client.ts"))
                .contains("encodeURIComponent(String(value))")
                .contains("headers.Authorization = `Bearer ${requiredEnvironment(\"API_AUTH_TOKEN\")}`")
                .doesNotContain("child_process", "exec(", "spawn(");
        assertThat(files.get("contract.json"))
                .contains("\"status\" : \"APPROVED\"")
                .contains("\"risk\" : \"READ_ONLY\"");
    }

    @Test
    void escapesReviewedTextInsteadOfTreatingItAsTypeScript() {
        McpToolContract original = approvedContract();
        McpToolContract.Tool tool = original.tools().getFirst();
        McpToolContract contract = new McpToolContract("1.0", "APPROVED", original.source(), List.of(
                new McpToolContract.Tool(tool.name(), "Quote \" and newline\nconsole.log('injected')",
                        tool.inputSchema(), tool.outputSchema(), tool.sourceOperation(),
                        tool.authenticationRequirements(), tool.risk())
        ));

        String source = generator.generate(contract).files().stream()
                .filter(file -> file.path().equals("src/index.ts"))
                .findFirst().orElseThrow().content();

        assertThat(source).contains("description: \"Quote \\\" and newline\\nconsole.log('injected')\"");
        assertThat(source.lines().filter(line -> line.trim().startsWith("console.log"))).isEmpty();
    }

    @Test
    void rejectsContractThatWasNotApproved() {
        McpToolContract approved = approvedContract();
        McpToolContract proposed = new McpToolContract(
                approved.contractVersion(), "PROPOSED", approved.source(), approved.tools());

        assertThatThrownBy(() -> generator.generate(proposed))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void rejectsPathParametersMissingFromInputSchema() {
        McpToolContract approved = approvedContract();
        McpToolContract.Tool tool = approved.tools().getFirst();
        var schema = objectMapper.createObjectNode().put("type", "object");
        schema.putObject("properties");
        McpToolContract malformed = new McpToolContract("1.0", "APPROVED", approved.source(), List.of(
                new McpToolContract.Tool(tool.name(), tool.description(), schema, tool.outputSchema(),
                        tool.sourceOperation(), tool.authenticationRequirements(), tool.risk())
        ));

        assertThatThrownBy(() -> generator.generate(malformed))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("path parameter");
    }

    private McpToolContract approvedContract() {
        var input = objectMapper.createObjectNode().put("type", "object");
        input.putObject("properties")
                .putObject("petId").put("type", "string");
        input.putArray("required").add("petId");
        input.put("additionalProperties", false);
        var output = objectMapper.createObjectNode().put("type", "object");
        output.putObject("properties").putObject("name").put("type", "string");
        return new McpToolContract(
                "1.0", "APPROVED",
                new McpToolContract.Source("FILE", "petstore.yaml", "3.1.0", "Pet Store", "1.0.0"),
                List.of(new McpToolContract.Tool(
                        "find_pets", "Find an available pet", input, output,
                        new McpToolContract.Operation("GET", "/pets/{petId}", "getPet"),
                        List.of("apiKey"), McpToolContract.Risk.READ_ONLY
                ))
        );
    }
}
