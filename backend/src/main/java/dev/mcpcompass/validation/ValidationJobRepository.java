package dev.mcpcompass.validation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ValidationJobRepository extends JpaRepository<ValidationJobEntity, UUID> {
}
