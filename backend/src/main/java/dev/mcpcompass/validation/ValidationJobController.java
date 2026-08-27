package dev.mcpcompass.validation;

import dev.mcpcompass.generation.McpToolContract;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/validation/jobs")
public class ValidationJobController {
    private final ValidationJobQueue queue;
    private final ValidationJobQuery query;

    ValidationJobController(ValidationJobQueue queue, ValidationJobQuery query) {
        this.queue = queue;
        this.query = query;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ValidationJobResponse> enqueue(@RequestBody McpToolContract contract) {
        ValidationJobResponse job = queue.enqueue(contract);
        return ResponseEntity.accepted()
                .body(job);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ValidationJobResponse> get(@PathVariable UUID id) {
        return query.find(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
