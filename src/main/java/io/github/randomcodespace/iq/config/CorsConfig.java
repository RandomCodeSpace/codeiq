package io.github.randomcodespace.iq.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

/**
 * CORS configuration for the {@code serving} profile.
 *
 * <p>The serving layer is strictly read-only: clients only need {@code GET} on the REST API
 * and {@code GET}/{@code POST} on the MCP streamable-HTTP endpoint. Mutating verbs
 * ({@code PUT}, {@code PATCH}, {@code DELETE}) are intentionally not allowed —
 * analysis happens locally via the CLI ({@code codeiq index} / {@code codeiq enrich})
 * and the server never accepts data manipulation.
 *
 * <p><b>Default is deny-all in serving.</b> The React UI is served same-origin from the
 * same Spring container, so cross-origin access is not required for normal operation.
 * Operators who genuinely need cross-origin access (e.g., serving the API behind a
 * reverse proxy at a different origin) must explicitly set
 * {@code codeiq.cors.allowed-origin-patterns} to a non-empty CSV — when empty, no CORS
 * mappings are registered and Spring MVC rejects all preflighted cross-origin requests.
 *
 * <p>Local development with the Vite dev server (running on a separate port) is the
 * usual reason to set this — typical value: {@code http://localhost:[*],http://127.0.0.1:[*]}.
 */
@Configuration
@Profile("serving")
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    /** Read-only REST API: only safe / preflight verbs. */
    static final String[] API_ALLOWED_METHODS = {"GET", "OPTIONS"};

    /** MCP streamable-HTTP: GET for SSE/handshake, POST for JSON-RPC frames, OPTIONS for preflight. */
    static final String[] MCP_ALLOWED_METHODS = {"GET", "POST", "OPTIONS"};

    /** Allow all request headers — clients commonly send custom MCP / Auth headers. */
    static final String ALLOWED_HEADERS = "*";

    /** Empty default = deny-all (no mappings registered). */
    private final String allowedOriginPatterns;

    public CorsConfig(@Value("${codeiq.cors.allowed-origin-patterns:}") String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns == null ? "" : allowedOriginPatterns;
    }

    @PostConstruct
    void logCorsState() {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isBlank()) {
            log.info("CORS: deny-all (no allowed-origin-patterns configured). "
                    + "Set codeiq.cors.allowed-origin-patterns to enable cross-origin access.");
        } else {
            log.info("CORS: allowed-origin-patterns = {}", allowedOriginPatterns);
        }
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        if (allowedOriginPatterns == null || allowedOriginPatterns.isBlank()) {
            // Deny-all: register no mappings. Spring MVC rejects cross-origin requests.
            return new WebMvcConfigurer() {};
        }
        String[] patterns = allowedOriginPatterns.split(",");
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(patterns)
                        .allowedMethods(API_ALLOWED_METHODS)
                        .allowedHeaders(ALLOWED_HEADERS);
                registry.addMapping("/mcp/**")
                        .allowedOriginPatterns(patterns)
                        .allowedMethods(MCP_ALLOWED_METHODS)
                        .allowedHeaders(ALLOWED_HEADERS);
            }
        };
    }
}
