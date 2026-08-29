package dev.mcpcompass.registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/internal/registry")
@ConditionalOnProperty(prefix = "app.registry.cron", name = "enabled", havingValue = "true")
public class RegistryCronSyncController {
    private final RegistrySyncService syncService;
    private final RegistryCronProperties properties;

    public RegistryCronSyncController(RegistrySyncService syncService, RegistryCronProperties properties) {
        if (!StringUtils.hasText(properties.secret())) {
            throw new IllegalStateException("Registry cron requires a non-empty secret");
        }
        this.syncService = syncService;
        this.properties = properties;
    }

    @GetMapping("/sync")
    public ResponseEntity<RegistrySyncService.SyncResult> sync(
            @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (!authorized(authorization)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        int maxPages = Math.min(Math.max(properties.maxPagesPerRun(), 1), 20);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(syncService.syncPages(maxPages));
    }

    private boolean authorized(String authorization) {
        byte[] expected = ("Bearer " + properties.secret()).getBytes(StandardCharsets.UTF_8);
        byte[] actual = (authorization == null ? "" : authorization).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}
