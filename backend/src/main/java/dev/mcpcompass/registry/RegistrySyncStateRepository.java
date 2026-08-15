package dev.mcpcompass.registry;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrySyncStateRepository extends JpaRepository<RegistrySyncStateEntity, String> {
}
