package dev.mcpcompass.selection;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/selection/events")
public class SelectionEventController {
    private final SelectionEventCounter counter;

    SelectionEventController(SelectionEventCounter counter) {
        this.counter = counter;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> record(@RequestBody JsonNode body) {
        if (!body.isString()) {
            return invalidEvent();
        }

        SelectionEvent event;
        try {
            event = SelectionEvent.valueOf(body.stringValue());
        } catch (IllegalArgumentException ignored) {
            return invalidEvent();
        }
        counter.increment(event);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Void> invalidEvent() {
        return ResponseEntity.badRequest().build();
    }
}
