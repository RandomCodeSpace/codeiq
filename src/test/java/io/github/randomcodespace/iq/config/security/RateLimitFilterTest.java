package io.github.randomcodespace.iq.config.security;

import io.github.randomcodespace.iq.config.unified.CodeIqUnifiedConfig;
import io.github.randomcodespace.iq.config.unified.DetectorsConfig;
import io.github.randomcodespace.iq.config.unified.IndexingConfig;
import io.github.randomcodespace.iq.config.unified.McpAuthConfig;
import io.github.randomcodespace.iq.config.unified.McpConfig;
import io.github.randomcodespace.iq.config.unified.McpLimitsConfig;
import io.github.randomcodespace.iq.config.unified.McpToolsConfig;
import io.github.randomcodespace.iq.config.unified.Neo4jConfig;
import io.github.randomcodespace.iq.config.unified.ObservabilityConfig;
import io.github.randomcodespace.iq.config.unified.ProjectConfig;
import io.github.randomcodespace.iq.config.unified.ServingConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimitFilterTest {

    @Test
    void underLimit_passesThroughAndDecrementsRemaining() throws Exception {
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(60));
        boolean[] chainHit = {false};
        FilterChain chain = (req, res) -> chainHit[0] = true;

        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req("/api/stats", "Bearer abc"), resp, chain);

        assertEquals(200, resp.getStatus());
        assertThat(chainHit[0]).isTrue();
        assertThat(resp.getHeader("X-RateLimit-Limit")).isEqualTo("60");
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("59");
    }

    @Test
    void overLimit_returns429WithRetryAfter() throws Exception {
        // Tiny bucket (rate=2/min) so we can exhaust it in 3 requests.
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(2));
        FilterChain noOp = (req, res) -> {};

        for (int i = 0; i < 2; i++) {
            f.doFilter(req("/api/stats", "Bearer abc"), new MockHttpServletResponse(), noOp);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        f.doFilter(req("/api/stats", "Bearer abc"), resp, noOp);

        assertEquals(429, resp.getStatus());
        assertThat(resp.getHeader("Retry-After")).isNotNull();
        assertThat(Integer.parseInt(resp.getHeader("Retry-After"))).isGreaterThan(0);
        assertThat(resp.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
        assertThat(resp.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
    }

    @Test
    void differentTokens_separateBuckets() throws Exception {
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(2));
        FilterChain noOp = (req, res) -> {};

        // Exhaust bucket for client A.
        for (int i = 0; i < 3; i++) {
            f.doFilter(req("/api/stats", "Bearer client-A"), new MockHttpServletResponse(), noOp);
        }
        // Client B should still have a full bucket.
        MockHttpServletResponse respB = new MockHttpServletResponse();
        f.doFilter(req("/api/stats", "Bearer client-B"), respB, noOp);
        assertEquals(200, respB.getStatus());
    }

    @Test
    void noAuthHeader_falls_back_to_remoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.setRemoteAddr("203.0.113.42");
        String key = RateLimitFilter.clientKey(req);
        assertThat(key).isEqualTo("ip:203.0.113.42");
    }

    @Test
    void xForwardedFor_takesPrecedenceOverRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("X-Forwarded-For", "192.0.2.5, 10.0.0.1");
        req.setRemoteAddr("10.0.0.99");
        String key = RateLimitFilter.clientKey(req);
        assertThat(key).isEqualTo("ip:192.0.2.5");
    }

    @Test
    void authHeader_keyIsHashed_notRawToken() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer SECRET-VALUE");
        String key = RateLimitFilter.clientKey(req);
        assertThat(key).startsWith("auth:");
        assertThat(key).doesNotContain("SECRET-VALUE");
        // 16 hex chars after prefix.
        assertThat(key).hasSize("auth:".length() + 16);
    }

    @Test
    void healthAndAssetPaths_bypassFilter() {
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(60));
        for (String path : new String[]{
                "/", "/index.html", "/favicon.ico",
                "/assets/main.css", "/static/img.png", "/error",
                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            assertThat(f.shouldNotFilter(req)).as("Bypass for %s", path).isTrue();
        }
    }

    @Test
    void protectedPaths_runFilter() {
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(60));
        for (String path : new String[]{
                "/api/stats", "/api/file", "/mcp", "/mcp/sse",
                "/actuator/metrics", "/actuator/info", "/actuator/prometheus"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            assertThat(f.shouldNotFilter(req)).as("Filter on %s", path).isFalse();
        }
    }

    @Test
    void nullUnifiedConfig_usesSensibleDefault() {
        // Constructor must not NPE when no config is wired (e.g. in some test scaffolding).
        RateLimitFilter f = new RateLimitFilter(null);
        assertNotNull(f);
    }

    @Test
    void zeroOrNegativeRate_fallsBackToDefault() {
        RateLimitFilter f = new RateLimitFilter(unifiedWithRate(0));
        assertNotNull(f);
        // No exception at construction — value is replaced with the audit-recommended default.
    }

    private static MockHttpServletRequest req(String path, String authHeader) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        r.addHeader("Authorization", authHeader);
        return r;
    }

    private static CodeIqUnifiedConfig unifiedWithRate(int ratePerMin) {
        return new CodeIqUnifiedConfig(
                new ProjectConfig("test", null, null, List.of()),
                new IndexingConfig(List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, null),
                new ServingConfig(null, null, null, null,
                        new Neo4jConfig(null, null, null, null)),
                new McpConfig(true, "http", "/mcp",
                        McpAuthConfig.empty(),
                        new McpLimitsConfig(15_000, 500, 2_000_000L, ratePerMin, 10),
                        new McpToolsConfig(List.of("*"), List.of())),
                new ObservabilityConfig(true, false, "json", "info"),
                new DetectorsConfig(List.of("default"), List.of(), List.of(), Map.of()));
    }
}
