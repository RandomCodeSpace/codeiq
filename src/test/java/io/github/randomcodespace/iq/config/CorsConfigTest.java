package io.github.randomcodespace.iq.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    static class TestableCorsRegistry extends CorsRegistry {
        @Override
        public Map<String, CorsConfiguration> getCorsConfigurations() {
            return super.getCorsConfigurations();
        }
    }

    private CorsConfig createCorsConfig() {
        return new CorsConfig();
    }

    @Test
    void corsConfigurerReturnsWebMvcConfigurer() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        assertNotNull(configurer);
    }

    @Test
    void corsConfigurerDoesNotThrowWhenAddingMappings() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        assertDoesNotThrow(() -> configurer.addCorsMappings(registry));
    }

    @Test
    void corsRegistryContainsApiAndMcpMappings() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var configurations = registry.getCorsConfigurations();
        assertTrue(configurations.containsKey("/api/**"),
                "Should register CORS mapping for /api/**");
        assertTrue(configurations.containsKey("/mcp/**"),
                "Should register CORS mapping for /mcp/**");
    }

    @Test
    void apiMappingAllowsExpectedMethods() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var configurations = registry.getCorsConfigurations();
        var apiCors = configurations.get("/api/**");
        assertNotNull(apiCors);
        var methods = apiCors.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("OPTIONS"));
    }

    @Test
    void mcpMappingAllowsGetPostOptions() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var configurations = registry.getCorsConfigurations();
        var mcpCors = configurations.get("/mcp/**");
        assertNotNull(mcpCors);
        var methods = mcpCors.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("OPTIONS"));
    }

    @Test
    void apiMappingRestrictsToLocalhostOrigins() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var configurations = registry.getCorsConfigurations();
        var apiCors = configurations.get("/api/**");
        assertNotNull(apiCors);
        var patterns = apiCors.getAllowedOriginPatterns();
        assertNotNull(patterns);
        assertTrue(patterns.stream().anyMatch(p -> p.contains("localhost")),
                "CORS should restrict to localhost origins");
    }

    @Test
    void apiMappingAllowsAllHeaders() {
        WebMvcConfigurer configurer = createCorsConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var configurations = registry.getCorsConfigurations();
        var apiCors = configurations.get("/api/**");
        assertNotNull(apiCors);
        var headers = apiCors.getAllowedHeaders();
        assertNotNull(headers);
        assertTrue(headers.contains("*"));
    }
}
