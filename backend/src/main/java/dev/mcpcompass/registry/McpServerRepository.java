package dev.mcpcompass.registry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface McpServerRepository extends JpaRepository<McpServerEntity, UUID>, JpaSpecificationExecutor<McpServerEntity> {
    Optional<McpServerEntity> findByRegistryName(String registryName);
}
