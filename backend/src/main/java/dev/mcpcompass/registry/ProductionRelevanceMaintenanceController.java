package dev.mcpcompass.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/internal/production-relevance")
@ConditionalOnProperty(prefix = "app.production-relevance.maintenance", name = "enabled", havingValue = "true")
class ProductionRelevanceMaintenanceController {
    private final ProductionRelevanceActivationService service;
    private final ProductionRelevanceMaintenanceProperties properties;

    ProductionRelevanceMaintenanceController(
            ProductionRelevanceActivationService service,
            ProductionRelevanceMaintenanceProperties properties
    ) {
        if (!StringUtils.hasText(properties.secret())) {
            throw new IllegalStateException("Production relevance maintenance requires a non-empty secret");
        }
        this.service = service;
        this.properties = properties;
    }

    @PostMapping("/activate")
    ProductionRelevanceActivationService.ActivationResult activate(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(defaultValue = "5") int maxPages,
            @RequestParam(defaultValue = "100") int embeddingBatchSize
    ) {
        if (!authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return service.activate(Math.min(maxPages, 20), embeddingBatchSize);
    }

    private boolean authorized(String authorization) {
        byte[] expected = ("Bearer " + properties.secret()).getBytes(StandardCharsets.UTF_8);
        byte[] actual = (authorization == null ? "" : authorization).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
