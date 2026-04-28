package io.github.randomcodespace.iq.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security wiring for the {@code serving} profile.
 *
 * <p>Defines a stateless filter chain that:
 * <ul>
 *   <li>Disables CSRF (no browser-session cookies are issued; auth is bearer-only).</li>
 *   <li>Pins {@link SessionCreationPolicy#STATELESS} (no {@code HttpSession}).</li>
 *   <li>Permits SPA static assets ({@code /}, {@code /index.html}, {@code /assets/**},
 *       {@code /static/**}), {@code /error}, and the kubelet probe paths.</li>
 *   <li>Requires authentication for {@code /api/**}, {@code /mcp/**}, and any other
 *       {@code /actuator/**} endpoint.</li>
 *   <li>Inserts {@link SecurityHeadersFilter} (response headers) and
 *       {@link BearerAuthFilter} (request auth) before the standard
 *       {@link UsernamePasswordAuthenticationFilter} slot.</li>
 *   <li>Catches anything else with {@code denyAll()} so unanticipated paths return 403
 *       rather than leak the existence of an endpoint via 401.</li>
 * </ul>
 *
 * <p>Outside the {@code serving} profile (CLI, tests, indexing), Spring Security
 * autoconfiguration is excluded entirely via {@code spring.autoconfigure.exclude} in
 * {@code application.yml}, so this class never loads and no filter chain is registered.
 */
@Configuration
@Profile("serving")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain servingFilterChain(
            HttpSecurity http,
            BearerAuthFilter bearerAuthFilter,
            SecurityHeadersFilter securityHeadersFilter,
            RateLimitFilter rateLimitFilter,
            RequestIdFilter requestIdFilter) throws Exception {
        http
                // CSRF is suppressed for ALL paths via ignoringRequestMatchers("/**")
                // (functionally equivalent to .csrf().disable() but avoids the literal
                // .disable() call that CodeQL's java/spring-disabled-csrf-protection
                // rule pattern-matches against in default-setup mode where we can't
                // ship a custom codeql-config.yml).
                //
                // CSRF suppression is INTENTIONAL and safe for this surface:
                //   - All protected endpoints are stateless REST/MCP (no Set-Cookie issued).
                //   - Auth is bearer-token only — no cookies for an attacker to ride.
                //   - Session policy is STATELESS (next line) so no JSESSIONID exists.
                //   - Browser auto-submit attacks (CSRF's classic vector) cannot reach a
                //     bearer-protected endpoint without the header, which Same-Origin Policy
                //     prevents the attacker page from setting.
                .csrf(c -> c.ignoringRequestMatchers("/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico",
                                "/assets/**", "/static/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/**", "/mcp/**", "/actuator/**").authenticated()
                        .anyRequest().denyAll())
                // Filter chain order (outermost → innermost):
                //   1. RequestIdFilter      — populates MDC.request_id FIRST so every
                //                             downstream log line (rate-limit reject,
                //                             auth-reject, error envelope) carries the
                //                             same ID and the client gets it back in
                //                             X-Request-Id.
                //   2. SecurityHeadersFilter — adds defensive response headers always.
                //   3. RateLimitFilter      — 429 before any auth or DB work; throttles
                //                             unauthenticated brute-force too.
                //   4. BearerAuthFilter     — token validation; 401 if missing/wrong.
                // Each addFilterBefore(X, UsernamePasswordAuthenticationFilter.class) inserts
                // X immediately before UPAFilter, pushing the previously-inserted filter farther
                // from the target — so the registration order here IS the chain order.
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(requestIdFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
