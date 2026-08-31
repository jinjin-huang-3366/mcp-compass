package dev.mcpcompass.server;

import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.registry.McpServerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpServerDetailController {
    private final McpServerRepository repository;

    public McpServerDetailController(McpServerRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<McpServerDetail> get(@PathVariable UUID id) {
        return repository.findById(id)
                .map(McpServerDetail::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record McpServerDetail(
            UUID id,
            String registryName,
            String title,
            String description,
            String version,
            String status,
            String repositoryUrl,
            String toolSchemaStatus,
            Instant toolSchemaInspectedAt,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
        static McpServerDetail from(McpServerEntity entity) {
            return new McpServerDetail(
                    entity.getId(), entity.getRegistryName(), entity.getTitle(), entity.getDescription(),
                    entity.getVersion(), entity.getStatus(), entity.getRepositoryUrl(), entity.getToolSchemaStatus(),
                    entity.getToolSchemaInspectedAt(), entity.getFirstSeenAt(), entity.getLastSeenAt()
            );
        }
    }
}
