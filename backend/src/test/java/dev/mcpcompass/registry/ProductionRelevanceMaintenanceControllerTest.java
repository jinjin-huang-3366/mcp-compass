package dev.mcpcompass.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductionRelevanceMaintenanceControllerTest {
    private final ProductionRelevanceActivationService service = mock(ProductionRelevanceActivationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductionRelevanceMaintenanceController(
                service, new ProductionRelevanceMaintenanceProperties(true, "maintenance-secret"))).build();
    }

    @Test
    void rejectsMissingAuthorization() throws Exception {
        mockMvc.perform(post("/api/v1/internal/production-relevance/activate"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void runsBoundedActivationWithConstantSecret() throws Exception {
        mockMvc.perform(post("/api/v1/internal/production-relevance/activate")
                        .header("Authorization", "Bearer maintenance-secret")
                        .queryParam("maxPages", "5")
                        .queryParam("embeddingBatchSize", "100"))
                .andExpect(status().isOk());

        verify(service).activate(5, 100);
    }
}
