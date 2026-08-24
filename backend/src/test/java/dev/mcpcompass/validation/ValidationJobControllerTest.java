package dev.mcpcompass.validation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationJobControllerTest {
    private static final UUID JOB_ID = UUID.fromString("bb62591b-bc88-4b64-a3ff-7330cc0158b3");

    @Test
    void acceptsAValidationJobForAsynchronousProcessing() throws Exception {
        ValidationJobQueue queue = mock(ValidationJobQueue.class);
        when(queue.enqueue(any())).thenReturn(new ValidationJobResponse(
                JOB_ID,
                ValidationJobStatus.QUEUED,
                "pet-store-mcp-server",
                Instant.parse("2026-08-24T14:30:00Z")
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValidationJobController(queue)).build();

        mockMvc.perform(post("/api/v1/validation/jobs")
                        .contentType("application/json")
                        .content(approvedContract()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.projectName").value("pet-store-mcp-server"))
                .andExpect(jsonPath("$.queuedAt").value("2026-08-24T14:30:00Z"));
    }

    private static String approvedContract() {
        return """
                {
                  "contractVersion":"1.0",
                  "status":"APPROVED",
                  "source":{"type":"FILE","location":"petstore.yaml","openApiVersion":"3.1.0","title":"Pet Store","apiVersion":"1.0.0"},
                  "tools":[{
                    "name":"find_pets",
                    "description":"Find available pets",
                    "inputSchema":{"type":"object","properties":{}},
                    "outputSchema":{"type":"object"},
                    "sourceOperation":{"method":"GET","path":"/pets","operationId":"findPets"},
                    "authenticationRequirements":[],
                    "risk":"READ_ONLY"
                  }]
                }
                """;
    }
}
