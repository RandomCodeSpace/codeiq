package io.github.randomcodespace.iq.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets defensive security headers on every response in the serving profile.
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — disables MIME sniffing.</li>
 *   <li>{@code X-Frame-Options: DENY} — clickjacking protection (also covered by CSP).</li>
 *   <li>{@code Content-Security-Policy} — restricts script/style/asset sources to self.
 *       {@code 'unsafe-inline'} on style is required by Ant Design / ECharts injected styles.</li>
 *   <li>{@code Referrer-Policy: no-referrer} — never leak the operator's URL on link clicks.</li>
 *   <li>{@code Permissions-Policy} — disables hardware features the SPA does not use.</li>
 *   <li>{@code Strict-Transport-Security} — set only when {@code X-Forwarded-Proto: https}
 *       (AKS terminates TLS at the ingress and forwards this header). Setting HSTS on
 *       plain HTTP would lock out clients in misconfigured envs.</li>
 * </ul>
 */
@Component
@Profile("serving")
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CSP =
            "default-src 'self'; "
                    + "script-src 'self'; "
                    + "style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; "
                    + "font-src 'self'; "
                    + "connect-src 'self'; "
                    + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy",
                "geolocation=(), camera=(), microphone=()");

        if ("https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
            response.setHeader("Strict-Transport-Security",
                    "max-age=31536000; includeSubDomains");
        }

        chain.doFilter(request, response);
    }
}
