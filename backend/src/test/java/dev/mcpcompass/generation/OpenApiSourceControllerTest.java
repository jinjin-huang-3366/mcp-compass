package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiSourceControllerTest {
    private static final byte[] DOCUMENT = """
            {"openapi":"3.1.0","info":{"title":"Pet Store","version":"1.0.0"},
             "paths":{"/pets":{"get":{},"post":{}}}}
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void acceptsMultipartFile() throws Exception {
        MockMvc mockMvc = mockMvc(uri -> DOCUMENT);
        MockMultipartFile file = new MockMultipartFile(
                "file", "petstore.json", "application/json", DOCUMENT
        );

        mockMvc.perform(multipart("/api/v1/generation/sources/openapi").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.sourceLocation").value("petstore.json"))
                .andExpect(jsonPath("$.title").value("Pet Store"))
                .andExpect(jsonPath("$.operationCount").value(2));
    }

    @Test
    void acceptsUrlRequest() throws Exception {
        MockMvc mockMvc = mockMvc(uri -> {
            if (!URI.create("https://developer.example.com/openapi.json").equals(uri)) {
                throw new AssertionError("Unexpected URI: " + uri);
            }
            return DOCUMENT;
        });

        mockMvc.perform(post("/api/v1/generation/sources/openapi")
                        .contentType("application/json")
                        .content("{\"url\":\"https://developer.example.com/openapi.json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("URL"))
                .andExpect(jsonPath("$.sourceLocation").value("https://developer.example.com/openapi.json"))
                .andExpect(jsonPath("$.openApiVersion").value("3.1.0"));
    }

    @Test
    void returnsStableErrorForInvalidDocument() throws Exception {
        MockMvc mockMvc = mockMvc(uri -> "not: openapi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/v1/generation/sources/openapi")
                        .contentType("application/json")
                        .content("{\"url\":\"https://developer.example.com/openapi.json\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OPENAPI_DOCUMENT"));
    }

    private static MockMvc mockMvc(OpenApiUrlFetcher fetcher) {
        OpenApiSourceProperties properties = new OpenApiSourceProperties(
                1024, Duration.ofSeconds(1), Duration.ofSeconds(1)
        );
        OpenApiSourceService service = new OpenApiSourceService(
                new OpenApiDocumentParser(new ObjectMapper()), fetcher, properties
        );
        return MockMvcBuilders.standaloneSetup(new OpenApiSourceController(service))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();
    }
}
