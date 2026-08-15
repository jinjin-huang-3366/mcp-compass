package dev.mcpcompass.registry;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/v1/dev/registry")
public class DevRegistrySyncController {
    private final RegistrySyncService syncService;

    public DevRegistrySyncController(RegistrySyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public RegistrySyncService.SyncResult sync(@RequestParam(defaultValue = "1") int maxPages) {
        return syncService.syncPages(Math.min(Math.max(maxPages, 1), 20));
    }
}
