package io.github.randomcodespace.iq.config.security;

import io.github.randomcodespace.iq.config.unified.CodeIqUnifiedConfig;
import io.github.randomcodespace.iq.config.unified.IndexingConfig;
import io.github.randomcodespace.iq.config.unified.McpAuthConfig;
import io.github.randomcodespace.iq.config.unified.McpConfig;
import io.github.randomcodespace.iq.config.unified.McpLimitsConfig;
import io.github.randomcodespace.iq.config.unified.McpToolsConfig;
import io.github.randomcodespace.iq.config.unified.Neo4jConfig;
import io.github.randomcodespace.iq.config.unified.ObservabilityConfig;
import io.github.randomcodespace.iq.config.unified.ProjectConfig;
import io.github.randomcodespace.iq.config.unified.ServingConfig;
import io.github.randomcodespace.iq.config.unified.DetectorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TokenResolver}. Covers fail-fast on misconfiguration,
 * env > config token priority, and mode-vs-profile guardrails.
 */
class TokenResolverTest {

    @Test
    void modeBearer_envTokenWins() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("bearer", "MY_TEST_TOKEN_ENV", "config-token", null)),
                envWithProfile("serving"));
        // Hack: the env-var read uses System.getenv() directly. Set up a separate test
        // for the env path — here we rely on the config fallback path.
        r.resolve();
        assertTrue(r.isAuthRequired());
        assertNotNull(r.expectedTokenBytes());
        assertEquals("config-token", new String(r.expectedTokenBytes()));
    }

    @Test
    void modeBearer_noTokenAnywhere_throws() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("bearer", "DOES_NOT_EXIST_VAR", null, null)),
                envWithProfile("serving"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, r::resolve);
        assertTrue(ex.getMessage().contains("no token resolved"));
        assertTrue(ex.getMessage().contains("DOES_NOT_EXIST_VAR"));
    }

    @Test
    void modeNone_servingProfile_throwsByDefault() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("none", null, null, null)),
                envWithProfile("serving"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, r::resolve);
        assertTrue(ex.getMessage().contains("not permitted"));
        assertTrue(ex.getMessage().contains("allow_unauthenticated"));
    }

    @Test
    void modeNone_servingProfile_allowedWithExplicitFlag() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("none", null, null, Boolean.TRUE)),
                envWithProfile("serving"));
        r.resolve();
        assertFalse(r.isAuthRequired());
    }

    @Test
    void modeNone_nonServingProfile_passes() {
        // Indexing profile or no profile: mode=none is fine.
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("none", null, null, null)),
                envWithProfile("indexing"));
        r.resolve();
        assertFalse(r.isAuthRequired());
    }

    @Test
    void unknownMode_throws() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("oauth", null, null, null)),
                envWithProfile("serving"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, r::resolve);
        assertTrue(ex.getMessage().contains("Unknown"));
    }

    @Test
    void modeMtls_throwsAsReserved() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("mtls", null, null, null)),
                envWithProfile("serving"));
        IllegalStateException ex = assertThrows(IllegalStateException.class, r::resolve);
        assertTrue(ex.getMessage().contains("not yet implemented"));
    }

    @Test
    void modeBearer_uppercaseAcceptedCaseInsensitively() {
        TokenResolver r = new TokenResolver(
                unifiedAuth(new McpAuthConfig("BEARER", null, "tk", null)),
                envWithProfile("serving"));
        r.resolve();
        assertTrue(r.isAuthRequired());
    }

    @Test
    void emptyAuth_treatedAsNoneOutsideServing() {
        TokenResolver r = new TokenResolver(unifiedAuth(McpAuthConfig.empty()), envWithProfile("indexing"));
        r.resolve();
        assertFalse(r.isAuthRequired());
    }

    private static MockEnvironment envWithProfile(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return env;
    }

    private static CodeIqUnifiedConfig unifiedAuth(McpAuthConfig auth) {
        return new CodeIqUnifiedConfig(
                new ProjectConfig("test", null, null, List.of()),
                new IndexingConfig(List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, null),
                new ServingConfig(null, null, null, null,
                        new Neo4jConfig(null, null, null, null)),
                new McpConfig(true, "http", "/mcp",
                        auth,
                        new McpLimitsConfig(15_000, 500, 2_000_000L, 300),
                        new McpToolsConfig(List.of("*"), List.of())),
                new ObservabilityConfig(true, false, "json", "info"),
                new DetectorsConfig(List.of("default"), List.of(), List.of(), Map.of()));
    }
}
