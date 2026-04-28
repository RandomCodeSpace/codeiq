package io.github.randomcodespace.iq.config.security;

import io.github.randomcodespace.iq.config.unified.CodeIqUnifiedConfig;
import io.github.randomcodespace.iq.config.unified.McpAuthConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Resolves the bearer token used by {@link BearerAuthFilter} from the unified
 * codeiq config + environment, and validates the configured auth mode against
 * the active Spring profile.
 *
 * <p>Token source priority (first non-blank wins):
 * <ol>
 *   <li>Env var named by {@code codeiq.mcp.auth.token_env} (default {@code CODEIQ_MCP_TOKEN})</li>
 *   <li>{@code codeiq.mcp.auth.token} from config — NOT recommended for production</li>
 * </ol>
 *
 * <p>Mode rules:
 * <ul>
 *   <li>{@code mode=bearer} requires a token; missing → fail-fast at startup.</li>
 *   <li>{@code mode=none} with active profile {@code serving} → fail-fast unless
 *       {@code allow_unauthenticated=true}. Set explicitly in non-prod only.</li>
 *   <li>Unknown mode → fail-fast (defensive — typos must not silently skip auth).</li>
 * </ul>
 */
@Component
@Profile("serving")
public class TokenResolver {

    private static final Logger log = LoggerFactory.getLogger(TokenResolver.class);
    static final String DEFAULT_TOKEN_ENV = "CODEIQ_MCP_TOKEN";
    static final String MODE_BEARER = "bearer";
    static final String MODE_NONE = "none";
    static final String MODE_MTLS = "mtls";

    private final CodeIqUnifiedConfig config;
    private final Environment environment;
    private byte[] expectedTokenBytes;
    private String mode;
    private boolean allowUnauthenticated;

    public TokenResolver(CodeIqUnifiedConfig config, Environment environment) {
        this.config = config;
        this.environment = environment;
    }

    @PostConstruct
    void resolve() {
        McpAuthConfig auth = (config.mcp() != null && config.mcp().auth() != null)
                ? config.mcp().auth() : McpAuthConfig.empty();
        String configuredMode = (auth.mode() == null || auth.mode().isBlank())
                ? MODE_NONE : auth.mode().toLowerCase(Locale.ROOT);
        this.mode = configuredMode;
        this.allowUnauthenticated = Boolean.TRUE.equals(auth.allowUnauthenticated());

        if (MODE_BEARER.equals(configuredMode)) {
            String envName = (auth.tokenEnv() != null && !auth.tokenEnv().isBlank())
                    ? auth.tokenEnv() : DEFAULT_TOKEN_ENV;
            String envToken = System.getenv(envName);
            String token = (envToken != null && !envToken.isBlank())
                    ? envToken
                    : (auth.token() != null && !auth.token().isBlank() ? auth.token() : null);
            if (token == null) {
                throw new IllegalStateException(
                        "codeiq.mcp.auth.mode=bearer but no token resolved. "
                                + "Set " + envName + " env var or codeiq.mcp.auth.token in config.");
            }
            this.expectedTokenBytes = token.getBytes(StandardCharsets.UTF_8);
            // CodeQL java/sensitive-log: log only the SOURCE category (env vs
            // config) — never the env-var name or token value, since both flow
            // from operator-controlled config which the data-flow analyzer
            // marks as tainted.
            if (envToken != null) {
                log.info("MCP auth: bearer token loaded from environment");
            } else {
                log.info("MCP auth: bearer token loaded from config file");
            }
        } else if (MODE_NONE.equals(configuredMode)) {
            if (servingActive() && !allowUnauthenticated) {
                throw new IllegalStateException(
                        "codeiq.mcp.auth.mode=none with `serving` profile is not permitted. "
                                + "Set mode=bearer (recommended) or "
                                + "codeiq.mcp.auth.allow_unauthenticated=true (NOT recommended).");
            }
            log.warn("MCP auth: DISABLED (mode=none). The /api and /mcp surfaces are unauthenticated.");
        } else if (MODE_MTLS.equals(configuredMode)) {
            throw new IllegalStateException(
                    "codeiq.mcp.auth.mode=mtls is reserved but not yet implemented.");
        } else {
            throw new IllegalStateException(
                    "Unknown codeiq.mcp.auth.mode: " + configuredMode + " (supported: bearer, none)");
        }
    }

    private boolean servingActive() {
        for (String p : environment.getActiveProfiles()) {
            if ("serving".equals(p)) return true;
        }
        return false;
    }

    /** True when bearer-token validation must be enforced on each request. */
    public boolean isAuthRequired() {
        return MODE_BEARER.equals(mode);
    }

    /** UTF-8 bytes of the expected token. Hashed at compare time — not the digest itself. */
    public byte[] expectedTokenBytes() {
        return expectedTokenBytes;
    }
}
