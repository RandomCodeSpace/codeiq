# Counter-Audit: serve-path Production Readiness
**Original audit:** `docs/audits/2026-04-28-serve-path-prod-readiness.md` (15 findings: 6 HIGH / 7 MEDIUM / 2 LOW)
**Counter-audit date:** 2026-04-28
**Method:** Every finding verified against actual source files. Net-new findings added from independent inspection of GraphController, McpTools, ServeCommand, GraphHealthIndicator, CorsConfig, BundleCommand, SafeFileReader, CodeIqConfig, McpLimitsConfig, McpAuthConfig, GraphStore, application.yml, pom.xml, security.yml, ci-java.yml, and frontend/package.json.

---

## Section A — Corrections to Original Audit

### A1. Finding #6 overstated — `markReady()` fires AFTER `bootstrapNeo4jFromCache()`, not before

**Original claim:** "`markReady()` fires before the graph is loaded" — traffic is routed during bootstrap.

**What the code actually does:** `ServeCommand.java` lines 83–126: `graphBootstrapper.bootstrapNeo4jFromCache()` is called at line 84 and returns only when bootstrap completes. `markReady()` is called at line 126, after that return and after the node/edge count is printed to stdout. The auto-bootstrap race window the audit describes does not exist in the current code.

**What IS real:** `GraphHealthIndicator` is not wired into the readiness group in `application.yml` (no `management.endpoint.health.group.readiness.include: readinessState,graph`). So the Kubernetes readiness probe does not gate on graph health. If a future code change moves `markReady()` earlier, or if bootstrap is made async, this becomes acute. The config gap is the real finding.

**Correction:** Downgrade finding #6 from HIGH to MEDIUM. The fix (adding `management.endpoint.health.group.readiness.include: readinessState,graph` to the serving profile in `application.yml`) is still valid and still needed, but the acute rollout-during-bootstrap race does not currently exist.

---

### A2. Finding #10 is half-wrong — REST `traceImpact` IS depth-capped; MCP is not

**Original claim:** "the API endpoint `/api/triage/impact/{id}` (`GraphController:188`) doesn't appear to bound it."

**What the code actually does:** `GraphController.java` line 192:
```java
int cappedDepth = Math.min(depth, config.getMaxDepth());
```
`config.getMaxDepth()` defaults to 10 (`CodeIqConfig.java`). The REST endpoint is bounded.

**What IS true — in the other direction:** `McpTools.traceImpact` passes `depth != null ? depth : 3` directly to `queryService.traceImpact` with no `Math.min` guard. The MCP path is unbounded.

**Correction:** The unbounded-depth defect exists on the MCP tool, not the REST controller. The fix targets `McpTools.traceImpact`, not `GraphController`. Severity (MEDIUM) is unchanged; affected surface is corrected. See also C3 below.

---

### A3. Finding #11 is partially wrong — `SafeFileReader` does enforce a byte cap

**Original claim:** "no `Content-Length` cap matches `getMaxFileBytes`" — implies size is unbounded.

**What the code actually does:** `GraphController.readFile` calls `SafeFileReader.read(resolved, startLine, endLine, config.getMaxFileBytes())`. `SafeFileReader` enforces the byte cap for both full-file and line-range reads. The cap is real and working.

**What IS real:** Content-type is not sniffed. Binary files (`.jks`, `.so`, `.png`) are served as `text/plain` with no early reject. The slow-client connection exhaustion concern is valid. The size-cap concern is not.

**Correction:** Remove "no Content-Length cap" from finding #11. The binary content-type concern stands; severity (MEDIUM) is unchanged.

---

## Section B — Severity Adjustments

### B1. Finding #6 — downgrade from HIGH to MEDIUM (per A1)

The bootstrap-before-markReady ordering eliminates the acute readiness race. The residual gap — readiness group not configured — is MEDIUM: probes do not gate on graph health, which can cause transient 503s after a pod restart if Neo4j is slow to open, but the bootstrap path completes before traffic is accepted under normal conditions.

---

### B2. Finding #4 — scope extended; severity remains HIGH

Finding #4 correctly flags null checksums in `BundleCommand.createManifest` (line 241: `null` passed as the checksums argument). This is confirmed. However the finding misses a co-equal defect: `generateServeShell` (lines 265–274 in `BundleCommand.java`) emits a `serve.sh` that unconditionally downloads the JAR at runtime:

```bash
curl -fL -o "$JAR" \
  "https://repo1.maven.org/maven2/io/github/randomcodespace/iq/code-iq/${VERSION}/code-iq-${VERSION}-cli.jar"
```

An equivalent `serve.bat` exists for Windows. This is a direct violation of `build.md` ("No runtime network calls to the public internet"). Bundles deployed in air-gapped environments silently fail to start when the JAR is absent. A compromised Maven Central namespace could substitute a malicious JAR, and even correct checksums in `manifest.json` would not protect against it because the downloaded JAR is not verified against them. The fix for finding #4 must address both the null checksums **and** the runtime download.

---

## Section C — Missed Findings

### C1. HIGH — `getCachedData()` loads the full graph into heap on every topology MCP tool call

**Symptom in prod:** Seven MCP tools — `serviceDetail`, `blastRadius`, `findPath`, `findBottlenecks`, `findCircularDeps`, `findDeadServices`, `findNode` — all begin by calling `getCachedData()` (`McpTools.java` lines 83–92). `getCachedData()` calls `graphStore.findAll()`, which executes two full graph scans (`findAllNodes` + `findAllEdges`) and materialises every node and every edge into a `GraphData` record on the Java heap. On a 5M-node enriched graph this is multiple gigabytes per call. No result is cached between invocations — each call pays the full allocation cost. Two concurrent `blast_radius` invocations double-allocate. This is an OOM vector independent of the `run_cypher` issue in finding #2, and it is triggered by normal topology tool usage, not by adversarial queries.

**File / location:** `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java:83–92` (`getCachedData`); `src/main/java/io/github/randomcodespace/iq/graph/GraphStore.java` (`findAll`, `findAllNodes`, `findAllEdges`).

**Severity:** HIGH

**Fix proposal:** Replace `getCachedData()` with targeted Cypher queries per tool (e.g. `blastRadius` needs only the subgraph reachable from the seed node, not all 5M nodes). If a full snapshot is required for correctness, populate it once at serve startup into a size-bounded `SoftReference` and invalidate on graph reload. Add a Caffeine cache with a 60-second TTL and a max-weight bound in bytes. Effort: M.

---

### C2. MEDIUM — Swagger UI exposed unauthenticated; full API schema readable by any cluster workload

**Symptom in prod:** `pom.xml` includes `springdoc-openapi-starter-webmvc-ui:3.0.3`. SpringDoc auto-registers `/swagger-ui/index.html`, `/swagger-ui.html`, and `/v3/api-docs` at startup with no authentication guard. Because there is no Spring Security on the classpath (finding #1), no filter intercepts these paths. Any actor who can reach the pod gets the complete OpenAPI schema: every endpoint path, parameter name, response shape, and the full enumeration of `NodeKind` / `EdgeKind` values. This is reconnaissance-in-depth that lowers the cost of exploiting finding #1. Neither `springdoc.swagger-ui.enabled` nor `springdoc.api-docs.enabled` is set to `false` in the serving profile of `application.yml`.

**File / location:** `pom.xml` (`springdoc-openapi-starter-webmvc-ui:3.0.3`); `src/main/resources/application.yml` (no `springdoc.*` keys in serving profile).

**Severity:** MEDIUM

**Fix proposal:** In `application.yml` serving profile: `springdoc.swagger-ui.enabled: false` and `springdoc.api-docs.enabled: false`. Provide opt-in `codeiq.serving.swagger-ui.enabled: true` for local development. When auth (finding #1) is implemented, gate `/swagger-ui/**` and `/v3/api-docs/**` behind the same bearer check. Effort: XS.

---

### C3. MEDIUM — `McpTools.traceImpact` has no depth cap on the MCP path

**Symptom in prod:** As established in A2, `McpTools.traceImpact` forwards caller-supplied `depth` to `queryService.traceImpact` without any `Math.min` guard. A malicious or runaway MCP client sends `depth=1000` on a hub node; the resulting `RELATES_TO*1..1000` variable-length Cypher match runs until the transaction timeout fires — which is also not configured (finding #2). The REST endpoint at `GraphController:192` is safe; the MCP surface is not. `McpLimitsConfig` already defines a `maxDepth` field that is never consumed here.

**File / location:** `src/main/java/io/github/randomcodespace/iq/mcp/McpTools.java` (`traceImpact` method, depth forwarding).

**Severity:** MEDIUM

**Fix proposal:** `int safedDepth = depth != null ? Math.min(depth, mcpLimitsConfig.maxDepth()) : 3;` — wire the already-parsed `maxDepth` from `McpLimitsConfig`. Effort: XS.

---

### C4. MEDIUM — `semgrep` installed from PyPI without a pinned version in `security.yml`

**Symptom in prod:** `.github/workflows/security.yml` line 94 runs `python -m pip install --quiet --upgrade pip semgrep` with no version pin. Every workflow run fetches the latest `semgrep` release from PyPI at the moment of execution. Every other tool in the same workflow is pinned: `osv-scanner` uses `OSV_SCANNER_VERSION: 2.3.5` with a named release download; `gitleaks` uses `GITLEAKS_VERSION: 8.30.1`; all GitHub Actions are SHA-pinned. A compromised PyPI release of `semgrep` (or a transitive dependency) would execute arbitrary code inside the SAST job, which runs with `contents: read` permission and access to `GITHUB_TOKEN`. This directly contradicts the workflow's header comment: "All actions SHA-pinned per Scorecard `Pinned-Dependencies`."

**File / location:** `.github/workflows/security.yml:94`.

**Severity:** MEDIUM

**Fix proposal:** Pin to a specific version: `pip install semgrep==<current-stable>` (resolve via PyPI). Better: use `returntocorp/semgrep-action` pinned by commit SHA (free for OSS), which eliminates the PyPI install entirely and aligns with the workflow's existing SHA-pinning posture. Effort: XS.

---

### C5. LOW — CLAUDE.md tech-stack version table is stale on three components

**Symptom:** CLAUDE.md "Tech Stack" section states Spring Boot `4.0.5`, Spring AI `2.0.0-M3`, Neo4j Embedded `2026.02.3`. Actual values in `pom.xml`: Spring Boot `4.0.6`, Spring AI `2.0.0-M4`, Neo4j `2026.04.0`. Stale docs cause reviewers and automated tooling to reference wrong versions when checking CVE databases or compatibility matrices.

**File / location:** `CLAUDE.md` (Tech Stack section); `pom.xml` (ground truth).

**Severity:** LOW

**Fix proposal:** Update three lines in CLAUDE.md. Note that `pom.xml` is the SSoT; CLAUDE.md is informational. Effort: XS.

---

## Summary Table

| # | Original Finding | Verdict | Adjustment |
|---|-----------------|---------|------------|
| 1 | No auth on API/MCP | Confirmed | — |
| 2 | `run_cypher` no cap / timeout / READ mode | Confirmed | — |
| 3 | No rate limiting | Confirmed | — |
| 4 | Unsigned bundle + null checksums | Confirmed + extended | serve.sh/bat runtime Maven Central download is co-equal defect; fix must cover both |
| 5 | `/api/file` ships secrets in bundle | Confirmed | — |
| 6 | Readiness fires before graph load | **Partially wrong** | Downgrade HIGH → MEDIUM; bootstrap-before-markReady ordering is correct; readiness group config gap is the real issue |
| 7 | No `@RestControllerAdvice`; stack trace leak | Confirmed | — |
| 8 | MCP errors return HTTP 200 | Confirmed | — |
| 9 | No structured logs / request ID / MDC | Confirmed | — |
| 10 | `findShortestPath` + `traceImpact` unbounded | **Partially wrong** | REST `traceImpact` IS capped via `Math.min`; MCP `traceImpact` is NOT; fix target corrected |
| 11 | `/api/file` no size cap; binary served as text | **Partially wrong** | Size cap IS enforced by SafeFileReader; binary content-type issue stands; remove size-cap claim |
| 12 | `GraphHealthIndicator` uncached count on every probe | Confirmed | — |
| 13 | CORS defaults wrong; no CSP / security headers | Confirmed | — |
| 14 | Bad YAML silently uses defaults; no fail-fast | Confirmed | — |
| 15 | Zero integration tests for auth / rate-limit path | Confirmed | — |
| C1 | `getCachedData()` full graph load per topology call | **NET NEW** | HIGH |
| C2 | Swagger UI unauthenticated | **NET NEW** | MEDIUM |
| C3 | MCP `traceImpact` no depth cap | **NET NEW** | MEDIUM |
| C4 | `semgrep` unpinned in `security.yml` | **NET NEW** | MEDIUM |
| C5 | CLAUDE.md version table stale | **NET NEW** | LOW |
