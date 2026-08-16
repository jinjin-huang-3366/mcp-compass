package dev.mcpcompass.registry;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegistrySyncStateRepository extends JpaRepository<RegistrySyncStateEntity, String> {
}
