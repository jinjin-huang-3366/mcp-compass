package dev.mcpcompass.server;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import dev.mcpcompass.registry.RegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpServerDetailControllerTest {
    @Test
    void returnsPublisherDeclaredRepositoryUrl() throws Exception {
        UUID id = UUID.fromString("d8f0ef86-fbbf-4778-98c0-1f53ff6e9ef9");
        Instant now = Instant.parse("2026-08-31T10:00:00Z");
        McpServerEntity server = McpServerEntity.create("io.example/github", now);
        server.updateFrom(new RegistryClient.RegistryServerPayload(
                "io.example/github",
                "GitHub MCP",
                "Read GitHub issues",
                "1.0.0",
                "active",
                "{}",
                true,
                "https://github.com/example/github-mcp",
                1,
                0,
                List.of(),
                List.of()
        ), now);
        McpServerRepository repository = mock(McpServerRepository.class);
        when(repository.findById(id)).thenReturn(Optional.of(server));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new McpServerDetailController(repository)).build();

        mockMvc.perform(get("/api/v1/mcp/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryUrl")
                        .value("https://github.com/example/github-mcp"));
    }
}
