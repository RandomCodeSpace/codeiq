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

    /** Default empty pattern = deny-all (production default). */
    private CorsConfig denyAllByDefault() {
        return new CorsConfig("");
    }

    /** Operator-configured loopback patterns (typical local-dev override). */
    private CorsConfig localDevConfig() {
        return new CorsConfig("http://localhost:[*],http://127.0.0.1:[*]");
    }

    @Test
    void corsConfigurerNeverNull() {
        // Both deny-all and explicit configs return a non-null configurer.
        assertNotNull(denyAllByDefault().corsConfigurer());
        assertNotNull(localDevConfig().corsConfigurer());
    }

    @Test
    void denyAllByDefault_registersNoMappings() {
        WebMvcConfigurer configurer = denyAllByDefault().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations = registry.getCorsConfigurations();
        assertFalse(configurations.containsKey("/api/**"),
                "Empty allowed-origin-patterns must NOT register /api/** CORS — deny-all is the default");
        assertFalse(configurations.containsKey("/mcp/**"),
                "Empty allowed-origin-patterns must NOT register /mcp/** CORS — deny-all is the default");
    }

    @Test
    void blankAllowedOriginPatterns_treatedAsDenyAll() {
        // Whitespace-only is the same as empty.
        WebMvcConfigurer configurer = new CorsConfig("   ").corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);
        assertTrue(registry.getCorsConfigurations().isEmpty(),
                "Blank allowed-origin-patterns must register no mappings");
    }

    @Test
    void explicitConfig_registersApiAndMcpMappings() {
        WebMvcConfigurer configurer = localDevConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations = registry.getCorsConfigurations();
        assertTrue(configurations.containsKey("/api/**"),
                "Explicit pattern should register CORS mapping for /api/**");
        assertTrue(configurations.containsKey("/mcp/**"),
                "Explicit pattern should register CORS mapping for /mcp/**");
    }

    @Test
    void explicitConfig_apiAllowsGetAndOptionsOnly() {
        WebMvcConfigurer configurer = localDevConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var apiCors = registry.getCorsConfigurations().get("/api/**");
        assertNotNull(apiCors);
        var methods = apiCors.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("OPTIONS"));
        // Mutating verbs must NOT be allowed — read-only API.
        assertFalse(methods.contains("PUT"));
        assertFalse(methods.contains("PATCH"));
        assertFalse(methods.contains("DELETE"));
    }

    @Test
    void explicitConfig_mcpAllowsGetPostOptions() {
        WebMvcConfigurer configurer = localDevConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var mcpCors = registry.getCorsConfigurations().get("/mcp/**");
        assertNotNull(mcpCors);
        var methods = mcpCors.getAllowedMethods();
        assertNotNull(methods);
        assertTrue(methods.contains("GET"));
        assertTrue(methods.contains("POST"));
        assertTrue(methods.contains("OPTIONS"));
    }

    @Test
    void explicitConfig_originPatternsPreserved() {
        WebMvcConfigurer configurer = localDevConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var apiCors = registry.getCorsConfigurations().get("/api/**");
        assertNotNull(apiCors);
        var patterns = apiCors.getAllowedOriginPatterns();
        assertNotNull(patterns);
        assertTrue(patterns.stream().anyMatch(p -> p.contains("localhost")),
                "Configured loopback pattern must reach the CORS configuration");
    }

    @Test
    void explicitConfig_allowsAllHeaders() {
        WebMvcConfigurer configurer = localDevConfig().corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);

        var apiCors = registry.getCorsConfigurations().get("/api/**");
        assertNotNull(apiCors);
        var headers = apiCors.getAllowedHeaders();
        assertNotNull(headers);
        assertTrue(headers.contains("*"));
    }

    @Test
    void nullPatterns_treatedAsDenyAll() {
        // Defensive — Spring binding can pass null in edge cases.
        WebMvcConfigurer configurer = new CorsConfig(null).corsConfigurer();
        TestableCorsRegistry registry = new TestableCorsRegistry();
        configurer.addCorsMappings(registry);
        assertTrue(registry.getCorsConfigurations().isEmpty(),
                "Null allowed-origin-patterns must register no mappings");
    }
}
