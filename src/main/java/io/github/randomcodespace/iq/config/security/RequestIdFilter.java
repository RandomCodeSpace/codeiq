package io.github.randomcodespace.iq.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter in the {@code serving} chain — populates {@code request_id} in
 * the SLF4J MDC for every request so downstream code (filters, controllers,
 * MCP tools, exception handlers) can read it via {@link MDC#get(String)} and
 * the JSON log encoder includes it on every emitted line.
 *
 * <p><b>ID source priority:</b>
 * <ol>
 *   <li>Inbound {@code X-Request-Id} header, if it matches a strict UUID-or-
 *       hex/dash pattern (defense against header injection — see CWE-117).
 *       This lets upstream load balancers / API gateways propagate trace IDs.</li>
 *   <li>Generated {@link UUID#randomUUID()} otherwise.</li>
 * </ol>
 *
 * <p>The same value is echoed back to the client in the {@code X-Request-Id}
 * response header so a caller seeing a 401/429/500 can quote it in support
 * channels and operators can grep their JSON logs for it.
 *
 * <p><b>MDC discipline.</b> The MDC is request-scoped via SLF4J's
 * {@code ThreadLocal} — the {@code finally} block clears it so the next
 * request on the same (virtual or platform) thread doesn't inherit a stale
 * ID. Without this, a thread-pool reuse leaks IDs across requests.
 *
 * <p><b>Filter chain position.</b> Registered FIRST in
 * {@code SecurityConfig#servingFilterChain} (before SecurityHeadersFilter,
 * RateLimitFilter, BearerAuthFilter) so the rate-limiter and auth-rejection
 * log lines all include the same {@code request_id} that the client receives
 * back in the response. If you reorder filters, this MUST stay outermost.
 */
@Component
@Profile("serving")
public class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String MDC_KEY = "request_id";

    /**
     * Strict allow-list for inbound {@code X-Request-Id} header values.
     * Hex digits, dashes, underscores, and length 8–64 — accommodates UUIDs
     * (with or without dashes), Stripe-style {@code req_*} (after stripping
     * the prefix in upstream gateways), and short hex correlation IDs.
     * Anything else gets replaced with a generated UUID — log forging via
     * {@code X-Request-Id: \nINFO: granted access} is impossible.
     */
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String inbound = request.getHeader(HEADER);
        String requestId = (inbound != null && VALID_REQUEST_ID.matcher(inbound).matches())
                ? inbound
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Always clear — virtual-thread carriers and Tomcat platform
            // threads both pool, so a leaked MDC entry from request N is
            // visible to request N+1 if we skip this.
            MDC.remove(MDC_KEY);
        }
    }
}
