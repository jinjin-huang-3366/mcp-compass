package dev.mcpcompass;

import dev.mcpcompass.embedding.EmbeddingProperties;
import dev.mcpcompass.registry.RegistryProperties;
import dev.mcpcompass.requirement.RequirementLlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        RegistryProperties.class,
        RequirementLlmProperties.class,
        EmbeddingProperties.class
})
public class McpCompassApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpCompassApplication.class, args);
    }
}
