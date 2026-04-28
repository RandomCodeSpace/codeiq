package io.github.randomcodespace.iq.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesUuidWhenNoInboundHeader() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn(null);

        AtomicReference<String> seenInsideChain = new AtomicReference<>();
        doAnswer(inv -> {
            seenInsideChain.set(MDC.get("request_id"));
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        assertNotNull(seenInsideChain.get(), "MDC must be populated during chain.doFilter");
        // Standard 36-char UUID with dashes
        assertTrue(seenInsideChain.get().matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
        verify(resp).setHeader("X-Request-Id", seenInsideChain.get());
    }

    @Test
    void preservesValidInboundHeader() throws ServletException, IOException {
        String upstream = "abc12345-trace-9999";
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn(upstream);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get("request_id"));
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        assertEquals(upstream, seen.get(), "Valid inbound ID must propagate unchanged");
        verify(resp).setHeader("X-Request-Id", upstream);
    }

    @Test
    void rejectsControlCharactersInInboundHeader() throws ServletException, IOException {
        // Log-injection attempt — newline embedded in upstream header
        String malicious = "abc\nINFO: granted access";
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn(malicious);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get("request_id"));
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        assertNotEquals(malicious, seen.get(),
                "Malformed inbound ID must be replaced with a generated UUID");
        assertFalse(seen.get().contains("\n"));
    }

    @Test
    void rejectsTooShortInboundHeader() throws ServletException, IOException {
        // Pattern requires 8-64 chars
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn("short");

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get("request_id"));
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        assertNotEquals("short", seen.get());
        assertTrue(seen.get().length() >= 8);
    }

    @Test
    void rejectsTooLongInboundHeader() throws ServletException, IOException {
        // Pattern caps at 64 chars to prevent log-bomb
        String tooLong = "a".repeat(65);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getHeader("X-Request-Id")).thenReturn(tooLong);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(inv -> {
            seen.set(MDC.get("request_id"));
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        assertNotEquals(tooLong, seen.get());
        assertTrue(seen.get().length() <= 64);
    }

    @Test
    void clearsMdcAfterChainCompletes() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertNull(MDC.get("request_id"),
                "MDC must be cleared post-chain to prevent leak across pooled threads");
    }

    @Test
    void clearsMdcEvenWhenChainThrows() throws ServletException, IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        doAnswer(inv -> { throw new ServletException("downstream failure"); })
                .when(chain).doFilter(any(), any());

        assertThrows(ServletException.class,
                () -> filter.doFilterInternal(req, resp, chain));

        assertNull(MDC.get("request_id"),
                "MDC must be cleared in finally even when downstream throws");
    }
}
