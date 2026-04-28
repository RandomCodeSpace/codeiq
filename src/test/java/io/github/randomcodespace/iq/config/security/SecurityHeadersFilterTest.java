package io.github.randomcodespace.iq.config.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void allHeadersPresentOnEveryResponse() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), resp, noOp());
        assertThat(resp.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(resp.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(resp.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(resp.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(resp.getHeader("Permissions-Policy")).contains("geolocation=()");
    }

    @Test
    void hstsSetWhenForwardedProtoIsHttps() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, noOp());
        assertThat(resp.getHeader("Strict-Transport-Security")).contains("max-age=31536000");
        assertThat(resp.getHeader("Strict-Transport-Security")).contains("includeSubDomains");
    }

    @Test
    void hstsNotSetOverPlainHttp() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), resp, noOp());
        assertNull(resp.getHeader("Strict-Transport-Security"));
    }

    @Test
    void cspBlocksFrameAncestors() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), resp, noOp());
        assertThat(resp.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
    }

    @Test
    void chainContinuesAfterHeadersSet() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean[] chainCalled = {false};
        FilterChain chain = (req, res) -> chainCalled[0] = true;
        filter.doFilter(new MockHttpServletRequest(), resp, chain);
        assertThat(chainCalled[0]).isTrue();
    }

    private static FilterChain noOp() {
        return (req, res) -> { /* no-op */ };
    }
}
