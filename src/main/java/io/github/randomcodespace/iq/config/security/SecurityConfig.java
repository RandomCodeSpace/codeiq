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
            SecurityHeadersFilter securityHeadersFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
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
                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(bearerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
