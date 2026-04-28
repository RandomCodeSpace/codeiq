package io.github.randomcodespace.iq.config.unified;

/**
 * MCP authentication configuration.
 *
 * <p>{@code mode} selects the authentication scheme. Supported values:
 * <ul>
 *   <li>{@code none} — no auth. Permitted only outside the {@code serving} profile,
 *       OR with {@code allowUnauthenticated=true} (logs a startup warning). Production
 *       deploys (serving profile) with {@code mode=none} fail-fast at startup.</li>
 *   <li>{@code bearer} — opaque bearer token. Source priority: {@code CODEIQ_MCP_TOKEN}
 *       env var > {@code token} field below > startup failure.</li>
 *   <li>{@code mtls} — reserved; not yet wired (tracked under follow-up).</li>
 * </ul>
 *
 * <p>{@code tokenEnv} is the env-var name to read the token from (defaults to
 * {@code CODEIQ_MCP_TOKEN} when null). {@code token} is a fallback in-config token —
 * not recommended for production (use the env var + a Kubernetes Secret); allowed for
 * local development. {@code allowUnauthenticated} is the explicit escape hatch for
 * {@code mode=none} in serving — must be set deliberately.
 */
public record McpAuthConfig(
        String mode,
        String tokenEnv,
        String token,
        Boolean allowUnauthenticated) {
    public static McpAuthConfig empty() { return new McpAuthConfig(null, null, null, null); }
}
