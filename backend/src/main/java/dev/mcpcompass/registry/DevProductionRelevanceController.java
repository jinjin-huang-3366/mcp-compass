package dev.mcpcompass.registry;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/production-relevance")
class DevProductionRelevanceController {
    private final ProductionRelevanceActivationService service;

    DevProductionRelevanceController(ProductionRelevanceActivationService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    ProductionRelevanceActivationService.ActivationResult activate(
            @RequestParam(defaultValue = "100") int maxPages,
            @RequestParam(defaultValue = "100") int embeddingBatchSize
    ) {
        return service.activate(maxPages, embeddingBatchSize);
    }
}
