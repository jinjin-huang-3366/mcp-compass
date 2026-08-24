package dev.mcpcompass.validation;

import dev.mcpcompass.generation.GeneratedProjectProvider;
import dev.mcpcompass.generation.GeneratedTypeScriptProject;
import dev.mcpcompass.generation.McpToolContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
class ValidationJobQueue {
    private static final Logger log = LoggerFactory.getLogger(ValidationJobQueue.class);

    private final GeneratedProjectProvider projectProvider;
    private final ValidationJobRepository repository;
    private final ObjectMapper objectMapper;

    ValidationJobQueue(
            GeneratedProjectProvider projectProvider,
            ValidationJobRepository repository,
            ObjectMapper objectMapper
    ) {
        this.projectProvider = projectProvider;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    ValidationJobResponse enqueue(McpToolContract contract) {
        GeneratedTypeScriptProject project = projectProvider.generate(contract);
        ValidationJobEntity job = ValidationJobEntity.queued(
                UUID.randomUUID(),
                project.projectName(),
                project.generatorVersion(),
                project.contractVersion(),
                objectMapper.writeValueAsString(project),
                Instant.now()
        );
        repository.save(job);
        log.info("Queued validation job {} for generated project {}", job.id(), job.projectName());
        return ValidationJobResponse.from(job);
    }
}
