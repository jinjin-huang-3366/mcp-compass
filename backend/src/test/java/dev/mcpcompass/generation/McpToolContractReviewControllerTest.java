package dev.mcpcompass.generation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpToolContractReviewControllerTest {
    @Test
    void approvesAReviewedSubset() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new McpToolContractReviewController(new McpToolContractReviewService()))
                .setControllerAdvice(new OpenApiSourceErrorHandler())
                .build();

        mockMvc.perform(post("/api/v1/generation/contracts/review")
                        .contentType("application/json")
                        .content("""
                                {
                                  "contract": {
                                    "contractVersion": "1.0",
                                    "status": "PROPOSED",
                                    "source": {"type":"FILE","location":"petstore.yaml","openApiVersion":"3.1.0","title":"Pet Store","apiVersion":"1.0"},
                                    "tools": [
                                      {"name":"list_pets","description":"List pets","inputSchema":{"type":"object"},"outputSchema":{"type":"array"},"sourceOperation":{"method":"GET","path":"/pets","operationId":"listPets"},"authenticationRequirements":[],"risk":"READ_ONLY"},
                                      {"name":"delete_pet","description":"Delete pet","inputSchema":{"type":"object"},"outputSchema":{"type":"object"},"sourceOperation":{"method":"DELETE","path":"/pets/{id}","operationId":"deletePet"},"authenticationRequirements":[],"risk":"DESTRUCTIVE"}
                                    ]
                                  },
                                  "tools": [
                                    {"toolIndex":0,"selected":true,"name":"find_pets","description":"Find available pets"},
                                    {"toolIndex":1,"selected":false,"name":"delete_pet","description":"Delete pet"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.tools.length()").value(1))
                .andExpect(jsonPath("$.tools[0].name").value("find_pets"))
                .andExpect(jsonPath("$.tools[0].sourceOperation.method").value("GET"));
    }
}
