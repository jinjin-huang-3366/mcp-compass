package dev.mcpcompass.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final CorsProperties properties;

    public WebConfig(CorsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(properties.allowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .exposedHeaders("Content-Disposition");
    }
}
