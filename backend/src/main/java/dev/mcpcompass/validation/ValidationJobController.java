package dev.mcpcompass.validation;

import dev.mcpcompass.generation.McpToolContract;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/validation/jobs")
public class ValidationJobController {
    private final ValidationJobQueue queue;

    ValidationJobController(ValidationJobQueue queue) {
        this.queue = queue;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<ValidationJobResponse> enqueue(@RequestBody McpToolContract contract) {
        ValidationJobResponse job = queue.enqueue(contract);
        return ResponseEntity.accepted()
                .body(job);
    }
}
