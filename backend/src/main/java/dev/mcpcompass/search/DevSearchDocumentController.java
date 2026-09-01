package dev.mcpcompass.search;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@Profile("local")
@RequestMapping("/api/v1/dev/search-documents")
class DevSearchDocumentController {
    private final SearchDocumentService service;

    DevSearchDocumentController(SearchDocumentService service) {
        this.service = service;
    }

    @PostMapping("/backfill")
    Map<String, Integer> backfill() {
        return Map.of("documents", service.backfill());
    }
}
