package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiToolContractDesignerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenApiDocumentParser parser = new OpenApiDocumentParser(objectMapper);
    private final OpenApiToolContractDesigner designer = new OpenApiToolContractDesigner(objectMapper);

    @Test
    void proposesReviewableToolsWithSchemasAuthSourceAndRisk() {
        McpToolContract contract = designer.design(document("""
                {
                  "openapi":"3.1.0",
                  "info":{"title":"Pet Store","version":"1.0.0"},
                  "security":[{"apiKey":[]}],
                  "paths":{"/pets/{petId}":{
                    "parameters":[{"name":"petId","in":"path","schema":{"type":"string"}}],
                    "get":{
                      "operationId":"getPet",
                      "summary":"Get one pet",
                      "parameters":[{"name":"includeOwner","in":"query","schema":{"type":"boolean"}}],
                      "responses":{"200":{"content":{"application/json":{"schema":{"type":"object","properties":{"name":{"type":"string"}}}}}}}
                    },
                    "delete":{"operationId":"deletePet","responses":{"204":{"description":"Deleted"}}}
                  }}
                }
                """));

        assertThat(contract.contractVersion()).isEqualTo("1.0");
        assertThat(contract.status()).isEqualTo("PROPOSED");
        assertThat(contract.source().title()).isEqualTo("Pet Store");
        assertThat(contract.tools()).hasSize(2);

        McpToolContract.Tool getPet = contract.tools().stream()
                .filter(tool -> tool.name().equals("get_pet"))
                .findFirst().orElseThrow();
        assertThat(getPet.description()).isEqualTo("Get one pet");
        assertThat(getPet.sourceOperation())
                .isEqualTo(new McpToolContract.Operation("GET", "/pets/{petId}", "getPet"));
        assertThat(getPet.authenticationRequirements()).containsExactly("apiKey");
        assertThat(getPet.risk()).isEqualTo(McpToolContract.Risk.READ_ONLY);
        assertThat(getPet.inputSchema().path("properties").has("petId")).isTrue();
        assertThat(getPet.inputSchema().path("properties").has("includeOwner")).isTrue();
        assertThat(getPet.inputSchema().path("required").get(0).stringValue()).isEqualTo("petId");
        assertThat(getPet.outputSchema().path("properties").has("name")).isTrue();

        assertThat(contract.tools().stream()
                .filter(tool -> tool.name().equals("delete_pet"))
                .findFirst().orElseThrow().risk()).isEqualTo(McpToolContract.Risk.DESTRUCTIVE);
    }

    @Test
    void derivesUniqueNamesAndIncludesJsonRequestBody() {
        McpToolContract contract = designer.design(document("""
                {"openapi":"3.0.3","info":{"title":"Orders","version":"1"},"paths":{
                  "/orders":{"post":{"operationId":"createOrder","requestBody":{"required":true,"content":{"application/json":{"schema":{"type":"object","required":["sku"],"properties":{"sku":{"type":"string"}}}}}},"responses":{}}},
                  "/orders/import":{"post":{"operationId":"createOrder","responses":{}}}
                }}
                """));

        assertThat(contract.tools()).extracting(McpToolContract.Tool::name)
                .containsExactly("create_order", "create_order_2");
        McpToolContract.Tool first = contract.tools().getFirst();
        assertThat(first.inputSchema().path("properties").path("body").path("type").stringValue())
                .isEqualTo("object");
        assertThat(first.inputSchema().path("required").get(0).stringValue()).isEqualTo("body");
        assertThat(first.risk()).isEqualTo(McpToolContract.Risk.MUTATING);
        assertThat(first.authenticationRequirements()).isEmpty();
    }

    private OpenApiSourceDocument document(String json) {
        OpenApiDocumentParser.ParsedOpenApiDocument parsed = parser.parse(json.getBytes(StandardCharsets.UTF_8));
        return new OpenApiSourceDocument(
                OpenApiSourceDocument.SourceKind.FILE,
                "api.json",
                parsed.openApiVersion(),
                parsed.title(),
                parsed.apiVersion(),
                parsed.pathCount(),
                parsed.operationCount(),
                parsed.document()
        );
    }
}
