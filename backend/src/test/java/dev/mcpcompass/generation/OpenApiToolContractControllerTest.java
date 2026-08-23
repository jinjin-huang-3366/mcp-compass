package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiToolContractControllerTest {
    private static final byte[] DOCUMENT = """
            {"openapi":"3.1.0","info":{"title":"Pet Store","version":"1.0.0"},
             "paths":{"/pets":{"get":{"operationId":"listPets","summary":"List pets","responses":{}}}}}
            """.getBytes(StandardCharsets.UTF_8);

    @Test
    void returnsProposedContractWithoutGeneratingCode() throws Exception {
        MockMvc mockMvc = mockMvc();
        MockMultipartFile file = new MockMultipartFile("file", "petstore.json", "application/json", DOCUMENT);

        mockMvc.perform(multipart("/api/v1/generation/contracts/openapi").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("1.0"))
                .andExpect(jsonPath("$.status").value("PROPOSED"))
                .andExpect(jsonPath("$.source.location").value("petstore.json"))
                .andExpect(jsonPath("$.tools[0].name").value("list_pets"))
                .andExpect(jsonPath("$.tools[0].sourceOperation.method").value("GET"))
                .andExpect(jsonPath("$.tools[0].risk").value("READ_ONLY"));
    }

    @Test
    void proposesContractFromAcceptedPublicUrl() throws Exception {
        mockMvc().perform(post("/api/v1/generation/contracts/openapi")
                        .contentType("application/json")
                        .content("{\"url\":\"https://developer.example.com/openapi.json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.type").value("URL"))
                .andExpect(jsonPath("$.source.location")
                        .value("https://developer.example.com/openapi.json"))
                .andExpect(jsonPath("$.tools[0].name").value("list_pets"));
    }

    private static MockMvc mockMvc() {
        ObjectMapper objectMapper = new ObjectMapper();
        OpenApiSourceService sourceService = new OpenApiSourceService(
                new OpenApiDocumentParser(objectMapper), uri -> DOCUMENT,
                new OpenApiSourceProperties(1024, Duration.ofSeconds(1), Duration.ofSeconds(1))
        );
        return MockMvcBuilders.standaloneSetup(new OpenApiToolContractController(
                        sourceService, new OpenApiToolContractDesigner(objectMapper)
                ))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();
    }
}
