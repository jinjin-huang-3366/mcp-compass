package dev.mcpcompass.registry;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevRegistrySyncControllerTest {
    private final RegistrySyncService syncService = mock(RegistrySyncService.class);

    @Test
    void localProfileRegistersTheController() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.registerBean(RegistrySyncService.class, () -> syncService);
            context.register(DevRegistrySyncController.class);

            context.refresh();

            assertThat(context.getBean(DevRegistrySyncController.class)).isNotNull();
        }
    }

    @Test
    void defaultProfileDoesNotRegisterTheController() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RegistrySyncService.class, () -> syncService);
            context.register(DevRegistrySyncController.class);

            context.refresh();

            assertThat(context.getBeansOfType(DevRegistrySyncController.class)).isEmpty();
        }
    }

    @Test
    void postSyncUsesTheDocumentedRoute() throws Exception {
        when(syncService.syncPages(1)).thenReturn(new RegistrySyncService.SyncResult(1, 3, "next"));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DevRegistrySyncController(syncService))
                .build();

        mockMvc.perform(post("/api/v1/dev/registry/sync").queryParam("maxPages", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pages").value(1))
                .andExpect(jsonPath("$.servers").value(3))
                .andExpect(jsonPath("$.nextCursor").value("next"));
        verify(syncService).syncPages(1);
    }
}
