package dev.mcpcompass.validation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                Instant.parse("2026-08-24T14:30:00Z"),
                null, null, null, null, null
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new ValidationJobController(queue, mock(ValidationJobQuery.class))
        ).build();

        mockMvc.perform(post("/api/v1/validation/jobs")
                        .contentType("application/json")
                        .content(approvedContract()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(JOB_ID.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.projectName").value("pet-store-mcp-server"))
                .andExpect(jsonPath("$.queuedAt").value("2026-08-24T14:30:00Z"));
    }

    @Test
    void returnsTheCompletedSecurityReport() throws Exception {
        ValidationJobQueue queue = mock(ValidationJobQueue.class);
        ValidationJobQuery query = mock(ValidationJobQuery.class);
        tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();
        when(query.find(JOB_ID)).thenReturn(java.util.Optional.of(new ValidationJobResponse(
                JOB_ID,
                ValidationJobStatus.EXECUTED,
                "pet-store-mcp-server",
                Instant.parse("2026-08-24T14:30:00Z"),
                Instant.parse("2026-08-24T14:31:00Z"),
                Instant.parse("2026-08-24T14:31:05Z"),
                null,
                objectMapper.readTree("{\"method\":\"tools/list\"}"),
                objectMapper.readTree("{\"overallRisk\":\"DESTRUCTIVE\",\"tools\":[{\"name\":\"delete_pet\",\"risk\":\"DESTRUCTIVE\"}]}")
        )));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValidationJobController(queue, query)).build();

        mockMvc.perform(get("/api/v1/validation/jobs/{id}", JOB_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED"))
                .andExpect(jsonPath("$.securityReport.overallRisk").value("DESTRUCTIVE"))
                .andExpect(jsonPath("$.securityReport.tools[0].name").value("delete_pet"));
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
