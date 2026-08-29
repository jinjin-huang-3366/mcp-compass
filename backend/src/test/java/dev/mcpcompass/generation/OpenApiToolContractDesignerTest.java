package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void resolvesLocalComponentReferencesIntoSelfContainedToolSchemas() {
        McpToolContract contract = designer.design(document("""
                {
                  "openapi":"3.1.0",
                  "info":{"title":"Pet Store","version":"1"},
                  "paths":{"/pets":{"post":{
                    "operationId":"createPet",
                    "requestBody":{"required":true,"content":{"application/json":{"schema":{"$ref":"#/components/schemas/NewPet"}}}},
                    "responses":{"201":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Pet"}}}}}
                  }}},
                  "components":{"schemas":{
                    "NewPet":{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}},
                    "Pet":{"allOf":[
                      {"$ref":"#/components/schemas/NewPet"},
                      {"type":"object","required":["id"],"properties":{"id":{"type":"string"}}}
                    ]}
                  }}
                }
                """));

        McpToolContract.Tool tool = contract.tools().getFirst();
        assertThat(tool.inputSchema().path("properties").path("body").path("type").stringValue())
                .isEqualTo("object");
        assertThat(tool.outputSchema().path("allOf").get(0).path("type").stringValue())
                .isEqualTo("object");
        assertThat(tool.inputSchema().toString()).doesNotContain("$ref");
        assertThat(tool.outputSchema().toString()).doesNotContain("$ref");
    }

    @Test
    void rejectsUnresolvableLocalSchemaReference() {
        assertThatThrownBy(() -> designer.design(document("""
                {
                  "openapi":"3.1.0",
                  "info":{"title":"Pet Store","version":"1"},
                  "paths":{"/pets":{"get":{
                    "operationId":"listPets",
                    "responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Missing"}}}}}
                  }}}
                }
                """)))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void rejectsCircularLocalSchemaReference() {
        assertThatThrownBy(() -> designer.design(document("""
                {
                  "openapi":"3.1.0",
                  "info":{"title":"Pet Store","version":"1"},
                  "paths":{"/pets":{"get":{
                    "operationId":"listPets",
                    "responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Pet"}}}}}
                  }}},
                  "components":{"schemas":{"Pet":{
                    "type":"object",
                    "properties":{"parent":{"$ref":"#/components/schemas/Pet"}}
                  }}}
                }
                """)))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("Circular");
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
