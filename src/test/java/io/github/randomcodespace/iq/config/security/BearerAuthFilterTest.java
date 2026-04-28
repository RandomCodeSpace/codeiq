package io.github.randomcodespace.iq.config.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BearerAuthFilter}. No Spring context — exercises the
 * filter directly with mock servlet objects to keep edge-case coverage tight.
 */
class BearerAuthFilterTest {

    private static final String TOKEN = "s3cret-bearer-token-value";
    private static final byte[] TOKEN_BYTES = TOKEN.getBytes(StandardCharsets.UTF_8);

    private TokenResolver resolver;
    private BearerAuthFilter filter;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        resolver = Mockito.mock(TokenResolver.class);
        when(resolver.isAuthRequired()).thenReturn(true);
        when(resolver.expectedTokenBytes()).thenReturn(TOKEN_BYTES);
        filter = new BearerAuthFilter(resolver);

        // Capture log lines so we can assert no token leakage.
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(BearerAuthFilter.class)).addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ((Logger) LoggerFactory.getLogger(BearerAuthFilter.class)).detachAppender(logAppender);
    }

    @Test
    void missingAuthorizationHeader_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        assertThat(resp.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(resp.getHeader("WWW-Authenticate")).startsWith("Bearer");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void wrongScheme_basic_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void lowercaseBearerScheme_accepted() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void wrongToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer wrong-token");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void correctToken_returns200() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        verify(chain, times(1)).doFilter(any(), any());
    }

    @Test
    void emptyToken_returns401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer ");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenValueNeverAppearsInLogs() throws Exception {
        ((Logger) LoggerFactory.getLogger(BearerAuthFilter.class)).setLevel(Level.DEBUG);
        String secret = "ABSOLUTELY-DO-NOT-LEAK";
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer " + secret);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        for (ILoggingEvent event : logAppender.list) {
            String line = event.getFormattedMessage();
            assertFalse(line.contains(secret),
                    "Token value leaked in log line: " + line);
        }
    }

    @Test
    void modeNoneAllowUnauth_passesThroughWithoutTokenCheck() throws Exception {
        when(resolver.isAuthRequired()).thenReturn(false);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        // No Authorization header.
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = Mockito.mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain, times(1)).doFilter(any(), any());
        assertEquals(200, resp.getStatus());
    }

    @Test
    void shouldNotFilter_openPaths() {
        // Static assets, SPA shell, error path, and kubelet probes bypass the filter.
        for (String path : new String[]{
                "/", "/index.html", "/favicon.ico", "/error",
                "/assets/index-abc.js", "/static/main.css",
                "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            assertTrue(filter.shouldNotFilter(req), "Expected bypass for: " + path);
        }
    }

    @Test
    void shouldFilter_protectedPaths() {
        for (String path : new String[]{
                "/api/stats", "/api/file?path=README.md",
                "/mcp", "/mcp/sse",
                "/actuator/metrics", "/actuator/info", "/actuator/prometheus"}) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
            assertFalse(filter.shouldNotFilter(req), "Expected filter to run for: " + path);
        }
    }

    /** SHA-256-pre-hash compare: lengths differ wildly but the result is deterministic. */
    @Test
    void isValidToken_lengthOracleDefense() {
        // Provided token is 1 byte, expected is 32 bytes — both go to 32-byte SHA-256.
        // The compare runs in constant time over 32-byte digests; result is just false.
        byte[] expected = "0123456789012345678901234567890123".getBytes(StandardCharsets.UTF_8);
        assertFalse(BearerAuthFilter.isValidToken("Bearer x", expected));
        assertFalse(BearerAuthFilter.isValidToken("Bearer xy", expected));
        assertFalse(BearerAuthFilter.isValidToken("Bearer xyz", expected));
        // No exception, no length-based crash.
    }

    @Test
    void isValidToken_correctTokenReturnsTrue() {
        assertTrue(BearerAuthFilter.isValidToken("Bearer " + TOKEN, TOKEN_BYTES));
    }

    @Test
    void isValidToken_nullSafe() {
        assertFalse(BearerAuthFilter.isValidToken(null, TOKEN_BYTES));
        assertFalse(BearerAuthFilter.isValidToken("Bearer " + TOKEN, null));
        assertFalse(BearerAuthFilter.isValidToken(null, null));
    }

    @Test
    void securityContextClearedAfterRequest() throws Exception {
        // A successful request sets SecurityContextHolder; verify we clear it
        // after dispatch so the principal doesn't leak into another virtual
        // thread that re-uses the carrier.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/stats");
        req.addHeader("Authorization", "Bearer " + TOKEN);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
            // Inside chain.doFilter — context should be set.
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        };

        filter.doFilter(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
