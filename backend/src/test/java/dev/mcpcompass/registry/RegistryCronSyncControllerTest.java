package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistryCronSyncControllerTest {
    private final RegistrySyncService syncService = mock(RegistrySyncService.class);
    private final RegistryCronProperties properties = new RegistryCronProperties(true, "test-secret", 5);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RegistryCronSyncController(syncService, properties))
            .build();

    @Test
    void rejectsRequestsWithoutTheConfiguredBearerSecret() throws Exception {
        mockMvc.perform(get("/api/v1/internal/registry/sync"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(syncService);
    }

    @Test
    void synchronizesTheConfiguredNumberOfPagesForVercelCron() throws Exception {
        when(syncService.syncPages(5)).thenReturn(new RegistrySyncService.SyncResult(5, 120, "next"));

        mockMvc.perform(get("/api/v1/internal/registry/sync")
                        .header("Authorization", "Bearer test-secret"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.pages").value(5))
                .andExpect(jsonPath("$.servers").value(120));

        verify(syncService).syncPages(5);
    }
}
