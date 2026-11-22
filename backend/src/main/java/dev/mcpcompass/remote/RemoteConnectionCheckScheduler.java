package dev.mcpcompass.remote;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.remote-check", name = "enabled", havingValue = "true")
public class RemoteConnectionCheckScheduler {
    private final RemoteConnectionCheckService service;

    public RemoteConnectionCheckScheduler(RemoteConnectionCheckService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${app.remote-check.fixed-delay-ms:3600000}")
    public void run() {
        service.checkDue();
    }
}
