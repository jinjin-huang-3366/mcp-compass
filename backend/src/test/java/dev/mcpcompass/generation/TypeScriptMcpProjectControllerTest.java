package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TypeScriptMcpProjectControllerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsGeneratedProjectFiles() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new TypeScriptMcpProjectController(new TypeScriptMcpProjectGenerator(
                                objectMapper, new TypeScriptMcpRuntimePack())))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        mockMvc.perform(post("/api/v1/generation/projects/typescript")
                        .contentType("application/json")
                        .content(contract("APPROVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("pet-store-mcp-server"))
                .andExpect(jsonPath("$.language").value("typescript"))
                .andExpect(jsonPath("$.files.length()").value(7))
                .andExpect(jsonPath("$.files[?(@.path == 'src/index.ts')]").exists());
    }

    @Test
    void rejectsUnapprovedContract() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new TypeScriptMcpProjectController(new TypeScriptMcpProjectGenerator(
                                objectMapper, new TypeScriptMcpRuntimePack())))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        mockMvc.perform(post("/api/v1/generation/projects/typescript")
                        .contentType("application/json")
                        .content(contract("PROPOSED")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APPROVED_CONTRACT"));
    }

    private static String contract(String status) {
        return """
                {
                  "contractVersion":"1.0",
                  "status":"%s",
                  "source":{"type":"FILE","location":"petstore.yaml","openApiVersion":"3.1.0","title":"Pet Store","apiVersion":"1.0.0"},
                  "tools":[{
                    "name":"find_pets",
                    "description":"Find available pets",
                    "inputSchema":{"type":"object","properties":{"petId":{"type":"string"}},"required":["petId"]},
                    "outputSchema":{"type":"object"},
                    "sourceOperation":{"method":"GET","path":"/pets/{petId}","operationId":"getPet"},
                    "authenticationRequirements":[],
                    "risk":"READ_ONLY"
                  }]
                }
                """.formatted(status);
    }
}
