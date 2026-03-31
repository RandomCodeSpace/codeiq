package io.github.randomcodespace.iq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("serving")
public class CorsConfig {

    @Value("${codeiq.cors.allowed-origin-patterns:http://localhost:[*],http://127.0.0.1:[*]}")
    private String allowedOriginPatterns = "http://localhost:[*],http://127.0.0.1:[*]";

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        String[] patterns = allowedOriginPatterns.split(",");
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(patterns)
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
                registry.addMapping("/mcp/**")
                        .allowedOriginPatterns(patterns)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
