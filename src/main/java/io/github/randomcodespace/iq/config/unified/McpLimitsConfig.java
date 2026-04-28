package io.github.randomcodespace.iq.config.unified;

/**
 * MCP per-call limits.
 *
 * <ul>
 *   <li>{@code perToolTimeoutMs} — wall-clock cap on a single tool invocation;
 *       wired into the Neo4j transaction timeout for {@code run_cypher} and
 *       graph traversals.</li>
 *   <li>{@code maxResults} — hard cap on rows returned by {@code run_cypher} and
 *       any unbounded list-returning tool. Excess rows are silently dropped and
 *       the response carries {@code truncated: true}.</li>
 *   <li>{@code maxPayloadBytes} — hard cap on the serialized response size for
 *       a single MCP tool call (defense against tiny-row * many-rows blowups).</li>
 *   <li>{@code ratePerMinute} — token-bucket refill for the per-client rate limit.</li>
 *   <li>{@code maxDepth} — hard cap on traversal depth for {@code trace_impact}
 *       and similar variable-length matches. Defends against
 *       {@code RELATES_TO*1..1000} blowups on hub nodes.</li>
 * </ul>
 */
public record McpLimitsConfig(Integer perToolTimeoutMs, Integer maxResults,
                             Long maxPayloadBytes, Integer ratePerMinute,
                             Integer maxDepth) {
    public static McpLimitsConfig empty() { return new McpLimitsConfig(null, null, null, null, null); }
}
