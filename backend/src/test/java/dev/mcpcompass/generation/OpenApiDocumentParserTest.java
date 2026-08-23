package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiDocumentParserTest {
    private final OpenApiDocumentParser parser = new OpenApiDocumentParser(new ObjectMapper());

    @Test
    void parsesYamlAndCountsOnlyHttpOperations() {
        byte[] source = """
                openapi: 3.1.0
                info:
                  title: Pet Store
                  version: 1.0.0
                paths:
                  /pets:
                    parameters: []
                    get:
                      operationId: listPets
                    post:
                      operationId: createPet
                """.getBytes(StandardCharsets.UTF_8);

        OpenApiDocumentParser.ParsedOpenApiDocument result = parser.parse(source);

        assertThat(result.openApiVersion()).isEqualTo("3.1.0");
        assertThat(result.title()).isEqualTo("Pet Store");
        assertThat(result.apiVersion()).isEqualTo("1.0.0");
        assertThat(result.pathCount()).isEqualTo(1);
        assertThat(result.operationCount()).isEqualTo(2);
        assertThat(result.document().path("paths").has("/pets")).isTrue();
    }

    @Test
    void parsesJsonDocument() {
        var result = parser.parse("""
                {"openapi":"3.0.3","info":{"title":"Orders","version":"2"},"paths":{}}
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(result.openApiVersion()).isEqualTo("3.0.3");
        assertThat(result.title()).isEqualTo("Orders");
        assertThat(result.pathCount()).isZero();
    }

    @Test
    void rejectsDocumentWithoutOpenApiVersion() {
        assertThatThrownBy(() -> parser.parse("info: {}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("'openapi'");
    }

    @Test
    void rejectsSwaggerTwoDocument() {
        assertThatThrownBy(() -> parser.parse("{\"openapi\":\"2.0\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(OpenApiSourceException.class)
                .hasMessageContaining("OpenAPI 3.x");
    }
}
