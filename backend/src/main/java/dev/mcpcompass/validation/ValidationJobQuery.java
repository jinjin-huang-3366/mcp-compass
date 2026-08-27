package dev.mcpcompass.validation;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@Service
class ValidationJobQuery {
    private final ValidationJobRepository repository;
    private final ObjectMapper objectMapper;

    ValidationJobQuery(ValidationJobRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    Optional<ValidationJobResponse> find(UUID id) {
        return repository.findById(id).map(job -> ValidationJobResponse.from(job, objectMapper));
    }
}
