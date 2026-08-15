package dev.mcpcompass;

import dev.mcpcompass.registry.RegistryProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RegistryProperties.class)
public class McpCompassApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpCompassApplication.class, args);
    }
}
