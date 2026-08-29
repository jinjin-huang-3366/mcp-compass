package dev.mcpcompass;

import dev.mcpcompass.config.CorsProperties;
import dev.mcpcompass.embedding.EmbeddingProperties;
import dev.mcpcompass.github.GithubEnrichmentProperties;
import dev.mcpcompass.generation.OpenApiSourceProperties;
import dev.mcpcompass.registry.RegistryCronProperties;
import dev.mcpcompass.registry.RegistryProperties;
import dev.mcpcompass.requirement.RequirementLlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
        RegistryCronProperties.class,
        RegistryProperties.class,
        CorsProperties.class,
        RequirementLlmProperties.class,
        EmbeddingProperties.class,
        GithubEnrichmentProperties.class,
        OpenApiSourceProperties.class
})
public class McpCompassApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpCompassApplication.class, args);
    }
}
