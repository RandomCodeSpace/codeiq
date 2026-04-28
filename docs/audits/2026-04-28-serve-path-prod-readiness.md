## 1. HIGH — MCP and REST API are fully unauthenticated; one curl from anywhere on the cluster reads the whole graph

**Symptom in prod:** Pod has no auth on `/api/**` or `/mcp` (no Spring Security on classpath, no `@PreAuthorize`, no filter, no token check). Any other workload in the AKS namespace — including a compromised sidecar in another tenant's pod that resolves the codeiq Service — can hit `GET /api/file?path=...` and exfiltrate every byte under the analyzed codebase root, plus run arbitrary read-only Cypher via `POST /mcp` `run_cypher`. The unified config defines `mcp.auth.mode: bearer|mtls` (`McpAuthConfig`) but **nothing wires it into a filter** — the field is dead. East-west attack on multi-tenant pipeline = data exfil from other tenants' analyzed source.

**File / location:** `src/main/java/io/github/randomcodespace/iq/api/GraphController.java:39` (no `@PreAuthorize`); `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java:269` (no auth check); `pom.xml` (no `spring-boot-starter-security`); `src/main/java/io/github/randomcodespace/iq/config/unified/McpAuthConfig.java` (config class, never consumed).

**Severity:** HIGH

**Fix proposal:** Add `spring-boot-starter-security`. Implement `SecurityFilterChain` in a new `config/SecurityConfig.java` that, when `codeiq.mcp.auth.mode=bearer`, requires `Authorization: Bearer ${CODEIQ_MCP_TOKEN}` on `/api/**` AND `/mcp/**` (constant-time compare). Permit only `/actuator/health/*`. Default `mode=none` permitted only when `spring.profiles.active` contains `local`. Effort: M.

---

## 2. HIGH — `run_cypher` has zero result-set cap, zero query timeout, and runs in the default (read+write) tx mode

**Symptom in prod:** A single MCP client sends `MATCH (a:CodeNode), (b:CodeNode), (c:CodeNode) RETURN a, b, c LIMIT 999999999`. `runCypher` accumulates rows in an `ArrayList<Map<String,Object>>` with no cap, the JVM heap fills, `OutOfMemoryError` triggers (heap dump goes to `/tmp` per `aks-launch.sh:51`, eats tmpfs), pod is `OOMKilled`. Tenant outage ≥60s while replica restarts and re-bootstraps Neo4j. Embedded Neo4j has no per-query memory limit configured (`Neo4jConfig.java`, no `dbms.memory.transaction.max_size`). Additionally, `tx.execute(query)` runs in default access mode, not READ — so a procedure registered later (or one this regex-blocklist misses) could mutate. The CLAUDE.md "Gotchas" already calls out RAN-31 ("pin run_cypher to Neo4j READ access mode") but the current code at `mcp/McpTools.java:296` still uses `graphDb.beginTx()` not `beginTx(KernelTransaction.Type.IMPLICIT, AUTH_DISABLED, AccessMode.Static.READ, timeoutMs, MILLIS)`.

**File / location:** `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java:269-318` (`runCypher`); `mcp/McpTools.java:311` (unbounded `rows.add`); `src/main/java/io/github/randomcodespace/iq/config/Neo4jConfig.java` (no transaction-timeout / memory settings).

**Severity:** HIGH

**Fix proposal:** Use `graphDb.beginTx(perToolTimeoutMs, MILLIS)` (transaction timeout already in `McpLimitsConfig.perToolTimeoutMs=15000`). Cap rows at `mcp.limits.max_results` (500) and stop iterating; return a `truncated: true` flag. Cap accumulated payload bytes at `mcp.limits.max_payload_bytes` (2 MB) by serializing-as-you-go. Configure `dbms.memory.transaction.max_size=512m` in `Neo4jConfig`. Effort: S.

---

## 3. HIGH — No rate limiting anywhere; one MCP client saturates the pod for everyone

**Symptom in prod:** `mcp.limits.rate_per_minute: 300` is defined in `McpLimitsConfig` and parsed by `UnifiedConfigLoader.java:166` but **no filter or interceptor enforces it** (zero hits for `Bucket4j|Resilience4j|RateLimiter|HandlerInterceptor` in main source). One agent client in a runaway loop fires `find_cycles` (which runs `MATCH p=(a)-[:RELATES_TO*2..10]->(a)` — graph-wide variable-length match, no per-call limit) at hundreds of QPS. Tomcat virtual-thread executor saturates Neo4j page cache, p99 on `/api/stats` jumps from 50 ms to multi-second, readiness probe (`periodSeconds: 5`) starts to flake, kubelet restarts the pod (`replicas: 1` — no failover), tenant goes dark.

**File / location:** `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java` (no rate limiter); `src/main/java/io/github/randomcodespace/iq/api/GraphController.java` (no rate limiter); `src/main/java/io/github/randomcodespace/iq/config/unified/McpLimitsConfig.java` (`ratePerMinute` parsed but unused).

**Severity:** HIGH

**Fix proposal:** Add Bucket4j (Apache-2.0, single dep, ~80 KB). Register an `OncePerRequestFilter` keyed by `Authorization` token (or remote IP fallback) with a refill-per-second token bucket sized at `mcp.limits.rate_per_minute / 60`. 429 with `Retry-After` header on bucket exhaustion. Apply to `/api/**` and `/mcp/**`. Effort: S.

---

## 4. HIGH — Bundle is unsigned and unverified; init-container blindly unzips whatever Nexus serves

**Symptom in prod:** AKS init-container (`shared/runbooks/aks-read-only-deploy.md:48-72`) runs `curl -u $NEXUS_USER:$NEXUS_PASS .../bundle.zip | unzip` with no checksum verification, no signature check. `ArtifactManifest` defines a `checksums` field (`Map<String,String>`) but `BundleCommand.createManifest` (`cli/BundleCommand.java`) passes `null` for it (sed shows `null` literal in the constructor call). On Nexus credential compromise OR a malicious internal user with `codeiq-bundles` write access, an attacker swaps `bundle.zip` with one that contains a `graph.db/` planted with a Cypher full-text index that triggers JNDI lookup, OR a `serve.sh` that is NEVER actually invoked at runtime but still — once bundles are signed, you can also trust `manifest.json`. Single tenant's bundle becomes a foothold across the whole pipeline because the same Nexus path is served to every replica.

**File / location:** `src/main/java/io/github/randomcodespace/iq/cli/BundleCommand.java:141-150` (manifest checksum field passed `null`); `src/main/java/io/github/randomcodespace/iq/intelligence/ArtifactManifest.java` (record defines `checksums` but never populated); `shared/runbooks/aks-read-only-deploy.md:48-72` (no `sha256sum -c` step).

**Severity:** HIGH

**Fix proposal:** In `BundleCommand`, after writing each entry, accumulate SHA-256 in a `MessageDigest` and emit the map. Write a sibling `bundle.zip.sha256` file uploaded next to the bundle. In the init-container, fetch `.sha256` first and `sha256sum -c` before unzip. For tamper-resistance, also sign with cosign / GPG (Sigstore = supply-chain consistent with §7.1 of engineering-standards). Effort: M.

---

## 5. HIGH — `/api/file` reads anything under the codebase root; bundle ships full source — credentials, .env, .pem all readable

**Symptom in prod:** `GraphController.readFile` (line 255) and `McpTools.readFile` (line 394) traverse-protect to the codebase root, but the bundle (`BundleCommand`, `source/` directory) ships **the entire source tree** including `.env`, `.aws/credentials` if committed, private keys checked in by mistake, secrets in `application-local.yml`. An authenticated MCP client (or unauthenticated, until #1 is fixed) calls `read_file(path=".env")` and prints the file. There is no extension allow-list, no `.gitignore`-aware filter at bundle time, no scrubber.

**File / location:** `src/main/java/io/github/randomcodespace/iq/api/GraphController.java:255-310` (`readFile`); `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java:394-420`; `src/main/java/io/github/randomcodespace/iq/cli/BundleCommand.java` (`source/` packaging — no exclusion).

**Severity:** HIGH

**Fix proposal:** At bundle time, exclude a curated set: `**/.env*`, `**/*.pem`, `**/*.key`, `**/id_rsa*`, `**/credentials`, `**/secrets/**`, anything matched by `.gitignore`. At read time, reject those same patterns even if they slip through. Add a `serving.read_file_extension_allowlist` config (default = source-code extensions only). Effort: S.

---

## 6. HIGH — `/actuator/health/readiness` returns 200 before the graph is loaded

**Symptom in prod:** `ServeCommand.markReady()` publishes `ReadinessState.ACCEPTING_TRAFFIC` after the Spring context is up, but `GraphHealthIndicator` (`health/GraphHealthIndicator.java`) is registered as a generic `HealthIndicator`, not under the readiness group. With Spring Boot's defaults, custom `HealthIndicator`s land in the liveness+readiness composite **only if they're added to the `readiness` group**. Right now: pod becomes "ready" the moment Spring starts (~8-16s per CLAUDE.md) but `GraphBootstrapper` is still loading H2 → Neo4j (can take seconds-to-minutes for big graphs). Readiness probe passes, kube-proxy routes traffic, every request 503s with "Neo4j graph not available" (`GraphController.requireQueryService:line ~30`). On rolling deploy this also means the new pod is marked ready before old pod is drained → 100% error rate during the rollover window.

**File / location:** `src/main/java/io/github/randomcodespace/iq/cli/ServeCommand.java:~110` (`markReady()`); `src/main/java/io/github/randomcodespace/iq/health/GraphHealthIndicator.java:1-40` (no readiness group); `application.yml` `serving` profile (`management.health.readinessstate.enabled: true` but no `management.endpoint.health.group.readiness.include: graph,readinessState`).

**Severity:** HIGH

**Fix proposal:** Move `markReady()` to fire **after** `GraphBootstrapper` returns AND `graphStore.count() > 0`. Add to `application.yml` (serving profile): `management.endpoint.health.group.readiness.include: readinessState,graph`. Add a regression test. Effort: S.

---

## 7. MEDIUM — No `@RestControllerAdvice`; uncaught exceptions return generic 500s with stack-trace bodies, no error envelope

**Symptom in prod:** `grep '@ControllerAdvice'` returns zero hits in `src/main/java`. When `QueryService.nodesByKind` throws (Neo4j tx died, NPE on a malformed cached node, etc.), Spring's default error attributes return a JSON body with `"trace": "...full stack..."` if `server.error.include-stacktrace` defaults haven't been turned off — and nothing in `application.yml` turns it off. On-call sees redacted `INTERNAL_SERVER_ERROR` in clients but the response body leaks classnames + line numbers (CWE-209). MCP tools partially mask this by returning `{"error": "..."}` 200 (which is its OWN problem — see finding #8). REST has no consistent error envelope at all.

**File / location:** `src/main/java/io/github/randomcodespace/iq/api/GraphController.java` (mixed `ResponseStatusException` + raw return); no `*ControllerAdvice*.java` files; missing `server.error.include-stacktrace=never` in `application.yml`.

**Severity:** MEDIUM

**Fix proposal:** Add `api/GlobalExceptionHandler.java` with `@RestControllerAdvice`. Map `ResponseStatusException` through, all others to `{"code": "INTERNAL", "message": <short>, "request_id": <MDC>}` with HTTP 500. Set `server.error.include-stacktrace: never` and `server.error.include-message: never` in the serving profile. Effort: S.

---

## 8. MEDIUM — MCP tools return `{"error": "..."}` with HTTP 200, defeating client retry logic and observability

**Symptom in prod:** Every `catch (Exception e)` in `McpTools` returns `toJson(Map.of(PROP_ERROR, e.getMessage()))` as a successful 200 response. Spring Boot metrics (`http.server.requests`) record these as 2xx, so error-rate dashboards stay green during incidents. MCP clients with retry-on-non-2xx never retry, never alert. Worse, `e.getMessage()` from a Neo4j parse error can leak query structure / node IDs from another tenant if a path-traversal bug ever lands.

**File / location:** `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java` (35+ `catch (Exception e) { return toJson(Map.of(PROP_ERROR, e.getMessage())); }` blocks).

**Severity:** MEDIUM

**Fix proposal:** Define error codes (`INVALID_INPUT`, `NOT_FOUND`, `INTERNAL`, `RATE_LIMITED`). Return MCP-spec-compliant errors (Spring AI MCP supports throwing — verify on its API). At minimum: log with stack trace at WARN, return `{"error": {"code": "INTERNAL", "message": "internal error", "request_id": ...}}` with the actual message redacted unless it's an `IllegalArgumentException`. Effort: S.

---

## 9. MEDIUM — No structured logs, no request ID, no MDC; on-call has no way to correlate a slow request to a Neo4j query

**Symptom in prod:** `grep MDC.put|requestId|X-Request-ID|OncePerRequestFilter` in `src/main/java`: zero hits. Pod logs are default Spring Boot text format. When customer reports "the graph endpoint hung for 30s at 14:32", on-call has only timestamp matching to find the query, no per-request span ID. With virtual threads enabled (`spring.threads.virtual.enabled: true`) and N concurrent slow requests, log lines interleave with no way to demux.

**File / location:** `src/main/resources/logback*.xml` (none — uses Spring Boot default); `src/main/resources/application.yml` (no `logging.pattern.level`); no `RequestIdFilter`.

**Severity:** MEDIUM

**Fix proposal:** Add `logback-spring.xml` with JSON appender (logstash-logback-encoder, MIT, single dep) gated on `spring.profiles.active=serving`. Add a `RequestIdFilter` (`OncePerRequestFilter`) that pulls `X-Request-ID` or generates a UUID, populates MDC, returns it in the response header. Add `Micrometer` timers around each `@McpTool` (Spring AI auto-instruments REST). Expose `/actuator/prometheus` (currently `metrics` is exposed but not the Prometheus scrape endpoint). Effort: M.

---

## 10. MEDIUM — `GraphStore.findShortestPath` and `traceImpact` have unbounded depth or fixed `[*..20]` with no row limit, no time guard

**Symptom in prod:** `GraphStore.findShortestPath` (line 453) runs `MATCH p = shortestPath((a)-[*..20]-(b)) RETURN [n IN nodes(p) | n.id]` — fine on small graphs, on a 5M-node enriched bundle this is 30+ seconds. `traceImpact` runs `MATCH (a)-[:RELATES_TO*1..$depth]->(b)` with `depth` capped at 10 by `McpTools.traceImpact:line ~349` — but the API endpoint `/api/triage/impact/{id}` (`GraphController:188`) doesn't appear to bound it. With 99 detector kinds and `RELATES_TO*1..10` on a hub node (e.g. a popular library import), this is a Cartesian explosion. No `WITH p LIMIT N` cap, no `dbms.transaction.timeout` configured.

**File / location:** `src/main/java/io/github/randomcodespace/iq/graph/GraphStore.java:453` (`shortestPath`); `:line for traceImpact`; `src/main/java/io/github/randomcodespace/iq/api/GraphController.java:188` (`triage/impact`).

**Severity:** MEDIUM

**Fix proposal:** Set `dbms.transaction.timeout=30s` in `Neo4jConfig`. Add `LIMIT $maxNodes` (e.g. 10000) on every `*..N` query. Bound `depth` ≤ 5 in REST endpoint and validate. Effort: S.

---

## 11. MEDIUM — `/api/file` content-type is `text/plain` for all files; binary data dumps; no `Content-Length` cap matches `getMaxFileBytes`

**Symptom in prod:** `readFile` returns binary files (a checked-in `.png`, `.jks` keystore, native `.so`) as `text/plain` with garbled UTF-8. Browser logs the entire base64-mangled body. The implementation reads via `SafeFileReader.read(resolved, startLine, endLine, config.getMaxFileBytes())` so size is bounded, but content-type isn't sniffed and there's no early-reject for non-text files. Slow client reading 1 MB file at 1 KB/s — keeps a virtual thread + a Tomcat connection occupied for 1000s.

**File / location:** `src/main/java/io/github/randomcodespace/iq/api/GraphController.java:255-310`.

**Severity:** MEDIUM

**Fix proposal:** Probe content type with `Files.probeContentType` or magic-byte check; if not `text/*`, return 415. Set `server.tomcat.connection-timeout=10s`, `server.tomcat.max-swallow-size=1MB`. Effort: S.

---

## 12. MEDIUM — `GraphHealthIndicator.health()` calls `graphStore.count()` on every probe — `MATCH (n:CodeNode) RETURN count(n)` against an embedded DB

**Symptom in prod:** Readiness probe `periodSeconds: 5` → 12 full Cypher count queries per minute, each holding a transaction open. On a 5M-node graph with concurrent user traffic, this contends with the page cache. Liveness probe also fires every 10s. The current implementation has no cache/throttle.

**File / location:** `src/main/java/io/github/randomcodespace/iq/health/GraphHealthIndicator.java:30`.

**Severity:** MEDIUM

**Fix proposal:** Cache `count()` result for 30 s in an `AtomicReference<CachedHealth>`. Or: only verify "graph reachable" via a constant-time `tx.execute("RETURN 1").hasNext()`. Effort: S.

---

## 13. MEDIUM — `CorsConfig` default allows `http://localhost:[*]` and `http://127.0.0.1:[*]`; in cluster, this is wrong but undetected; no CSP

**Symptom in prod:** Default `codeiq.cors.allowed-origin-patterns` (`config/CorsConfig.java:14`) is hardcoded to dev-loopback patterns. In AKS, the React UI is served same-origin (no CORS needed) — this is fine — but if anyone exposes the API behind a reverse proxy at a different origin, they'll get cryptic CORS failures because the YAML doesn't override it (`codeiq.yml.example` doesn't even include it). Worse: zero CSP / X-Frame-Options / X-Content-Type-Options headers means the served React UI is clickjackable and the JSON endpoints can be loaded into a hostile origin's `<iframe>` (defense-in-depth violation, OpenSSF Scorecard `Token-Permissions` adjacent).

**File / location:** `src/main/java/io/github/randomcodespace/iq/config/CorsConfig.java:14`; no security-headers filter.

**Severity:** MEDIUM

**Fix proposal:** Default CORS to **deny-all** in the serving profile; require explicit `codeiq.cors.allowed_origin_patterns` opt-in (fail-fast log warning if empty + non-loopback bind). Add a `SecurityHeadersFilter` setting `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'`, `Referrer-Policy: no-referrer`. Effort: S.

---

## 14. LOW — `ConfigValidator.validate` returns errors but `UnifiedConfigLoader` consumers don't `System.exit` on serving startup; bad YAML silently uses defaults

**Symptom in prod:** Operator typos `serving.port: "eight080"` in `codeiq.yml`. `UnifiedConfigLoader.requireIntOrNull` returns null → the field falls back to its default → pod listens on the **default** port, not what was configured. Probe and Service definitions point at the wrong port → `ConnectionRefused`. Hours of debugging. Need fail-fast.

**File / location:** `src/main/java/io/github/randomcodespace/iq/config/unified/UnifiedConfigLoader.java`; `src/main/java/io/github/randomcodespace/iq/config/unified/ConfigValidator.java:20`.

**Severity:** LOW

**Fix proposal:** In `ServeCommand.call()`, run `ConfigValidator.validate(unifiedConfig)`; if `!errors.isEmpty()`, log all errors and `return 1` before Spring context starts. Effort: S.

---

## 15. LOW — Test coverage gap: zero integration tests for the auth + rate-limit + error-envelope path; `run_cypher` tests stub `tx.execute` (never exercise embedded Neo4j)

**Symptom in prod:** Findings #1, #2, #3, #7, #8 above are all defects whose fixes need integration tests against a real embedded Neo4j. Today: `McpToolsTest` / `McpToolsExpandedTest` use `@Mock Transaction tx` (visible in the diff snippet). `GraphControllerTest` uses `MockMvcBuilders.standaloneSetup` — bypasses any filter chain, so a future auth filter wouldn't be regression-tested at the controller level.

**File / location:** `src/test/java/io/github/randomcodespace/iq/api/GraphControllerTest.java`; `src/test/java/io/github/randomcodespace/iq/mcp/McpToolsTest.java`; missing `@SpringBootTest(profiles="serving")` integration test class.

**Severity:** LOW

**Fix proposal:** Add `ServeProfileIntegrationTest` with `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("serving")`, populate Neo4j with a fixture, exercise `run_cypher` rate limit + auth header + error envelope end-to-end. Effort: M.
