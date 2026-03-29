# Service Topology Design

**Date:** 2026-03-29
**Status:** Approved
**Scope:** AppDynamics-style service topology from static code analysis, multi-repo support, MCP-first interface

## Overview

Build a service topology layer that maps codebases into services, their connections (HTTP, gRPC, messaging, database, external APIs), and provides MCP tools for AI-powered triage. Supports both monorepo and multi-repo by treating every repo as a monorepo with modules.

## Use Cases

1. **Business function discovery** — "How does the payment flow work?" → Agent traces services, endpoints, entities, dependencies, reads actual code
2. **Stacktrace triage** — "NullPointerException at OrderService.java:142" → Agent finds service, traces callers, impact, reads code around the error
3. **Performance bottleneck** — "GET /api/orders is slow" → Agent finds service, traces all downstream dependencies, identifies hub services
4. **Root cause analysis** — "Users can't login" → Agent searches auth-related code, finds circular dependencies, reads auth logic

## Service Model

### SERVICE as a first-class node

New `NodeKind.SERVICE` — created during analysis. Every child node gets a `service` property for fast filtering.

**Service detection (in priority order):**
1. Auto-detect modules: directories with `pom.xml`, `package.json`, `go.mod`, `build.gradle`, `Cargo.toml`, `*.csproj`
2. Each detected module = a SERVICE node
3. If NO modules detected → entire repo = one SERVICE (named after project directory)
4. `--service-name` CLI flag overrides the project-level name

**Multi-repo:** No special handling. Scan each repo into the same graph. Each repo's modules become services. The topology connects them via shared topics, API paths, and database names.

```bash
# Scan multiple repos into same graph
code-iq analyze /path/to/order-service --service-name order-service
code-iq analyze /path/to/auth-service --service-name auth-service
code-iq analyze /path/to/shared-lib --service-name shared-lib

# View topology
code-iq serve
```

### Service node properties

```java
CodeNode service = new CodeNode();
service.setKind(NodeKind.SERVICE);
service.setLabel("order-service");
service.setFilePath("services/order/");
service.getProperties().put("build_tool", "maven");
service.getProperties().put("languages", List.of("java", "kotlin"));
service.getProperties().put("endpoint_count", 12);
service.getProperties().put("entity_count", 6);
service.getProperties().put("detected_from", "pom.xml");  // or package.json, go.mod, etc.
```

## Topology Connections

### Runtime connections only (default)

| Edge Kind | Meaning | Example |
|---|---|---|
| CALLS | Service A calls Service B (HTTP/gRPC/RMI) | order-service → auth-service |
| PRODUCES | Service sends to message queue | order-service → Kafka:order.created |
| CONSUMES | Service reads from message queue | notification-service ← Kafka:order.created |
| QUERIES | Service connects to database | order-service → PostgreSQL:orders_db |
| CONNECTS_TO | Service connects to external system | auth-service → LDAP:corp-ldap |

### Build dependencies excluded from topology

DEPENDS_ON, IMPORTS → belong in SBOM/dependency analysis, not service topology. These don't represent runtime connections.

### Configurable via .osscodeiq.yml

```yaml
topology:
  # Which edge kinds appear as service connections
  connections:
    - CALLS
    - PRODUCES
    - CONSUMES
    - QUERIES
    - CONNECTS_TO
    # Uncomment to include build dependencies:
    # - DEPENDS_ON

  # Custom service grouping (optional, overrides auto-detect)
  services:
    order-service:
      paths: [services/order/**]
    auth-service:
      paths: [services/auth/**]
```

Default (no config): runtime connections only.

## MCP Tools — 17 Total

### New Topology Tools (10)

**`get_topology()`**
Returns the full service map: all SERVICE nodes + connections between them.
```json
{
  "services": [
    {"name": "order-service", "endpoints": 12, "entities": 6, "connections_out": 4, "connections_in": 2},
    {"name": "auth-service", "endpoints": 8, "entities": 3, "connections_out": 2, "connections_in": 5}
  ],
  "connections": [
    {"source": "order-service", "target": "auth-service", "type": "CALLS", "protocol": "HTTP"},
    {"source": "order-service", "target": "Kafka:order.created", "type": "PRODUCES"},
    {"source": "order-service", "target": "PostgreSQL:orders_db", "type": "QUERIES"}
  ]
}
```

**`service_detail(service_name)`**
Drill into a service — all endpoints, entities, guards, configs, connections.
```json
{
  "name": "order-service",
  "endpoints": [{"id": "...", "label": "GET /api/orders", "method": "GET"}],
  "entities": [{"id": "...", "label": "Order", "table": "orders"}],
  "guards": [{"id": "...", "label": "JwtGuard"}],
  "databases": [{"id": "...", "label": "PostgreSQL:orders_db"}],
  "queues": [{"id": "...", "label": "Kafka:order.created", "role": "producer"}],
  "frameworks": ["spring", "jpa", "kafka"],
  "files": 28,
  "nodes": 78,
  "edges": 112
}
```

**`service_dependencies(service_name)`**
What does this service depend on — other services, databases, queues, external systems.

**`service_dependents(service_name)`**
What depends on this service — who calls it, who consumes its events.

**`blast_radius(node_id)`**
If I change this node, what services and endpoints are affected. Traces outward through CALLS, PRODUCES, CONSUMES edges.

**`find_path(source, target)`**
How does service A connect to service B — full path with intermediaries (e.g., order-service → Kafka → notification-service).

**`find_bottlenecks()`**
Services with the most connections (high in-degree + out-degree). Potential performance risks.

**`find_circular_deps()`**
Circular service-to-service dependencies. A → B → C → A.

**`find_dead_services()`**
Services with no incoming connections — nobody calls them.

**`find_node(query)`**
Targeted node lookup — exact label match priority, then partial match. Returns fewer, more relevant results than search_graph.

### Enhanced Existing Tools (1)

**`read_source_file(path, start_line, end_line)`**
Add optional `start_line` and `end_line` parameters to return a specific range instead of the whole file. Essential for stacktrace triage — "show me lines 130-160 of OrderService.java".

### Unchanged Existing Tools (6)

- `find_component_by_file(file)` — stacktrace → component mapping
- `trace_impact(node_id, depth)` — downstream impact
- `find_callers(node_id)` — who calls this
- `search_graph(query)` — free-text search
- `find_related_endpoints(id)` — entity → endpoints
- `get_detailed_stats(category)` — categorized stats

## REST API Endpoints

Mirror all MCP tools as REST:

```
GET /api/topology                          → get_topology()
GET /api/topology/services/{name}          → service_detail()
GET /api/topology/services/{name}/deps     → service_dependencies()
GET /api/topology/services/{name}/dependents → service_dependents()
GET /api/topology/blast-radius/{nodeId}    → blast_radius()
GET /api/topology/path?from=A&to=B         → find_path()
GET /api/topology/bottlenecks              → find_bottlenecks()
GET /api/topology/circular                 → find_circular_deps()
GET /api/topology/dead                     → find_dead_services()
GET /api/nodes/find?q=OrderService         → find_node()
```

## Implementation Components

### ServiceDetector (new detector)

A special detector that runs AFTER all other detectors + linkers:
1. Scans the graph for module boundaries (pom.xml, package.json, etc.)
2. Creates SERVICE nodes
3. Sets `service` property on all child nodes
4. Creates CONTAINS edges from SERVICE to child nodes

### TopologyService (new service)

```java
@Service
public class TopologyService {
    public Map<String, Object> getTopology() { ... }
    public Map<String, Object> serviceDetail(String name) { ... }
    public Map<String, Object> serviceDependencies(String name) { ... }
    public Map<String, Object> serviceDependents(String name) { ... }
    public Map<String, Object> blastRadius(String nodeId) { ... }
    public List<Map<String, Object>> findPath(String source, String target) { ... }
    public List<Map<String, Object>> findBottlenecks() { ... }
    public List<List<String>> findCircularDeps() { ... }
    public List<Map<String, Object>> findDeadServices() { ... }
    public List<Map<String, Object>> findNode(String query) { ... }
}
```

### TopologyController (REST)

`@RestController @RequestMapping("/api/topology")` with all endpoints above.

### MCP Tools Enhancement

Add 10 new `@Tool` methods to `McpTools.java`. Enhance `read_source_file` with line range.

## Visualization

### Interactive (Cytoscape.js)

Service topology rendered as a Cytoscape graph:
- SERVICE nodes as large rounded rectangles
- Database/Queue/External as distinct shapes (cylinder, diamond, cloud)
- Edges labeled with connection type
- Click service → drill down to service_detail
- Click connection → show find_path

### Static Export

```bash
code-iq topology /path/to/repo --format mermaid > topology.mmd
code-iq topology /path/to/repo --format svg > topology.svg
```

## CLI Command

```bash
code-iq topology [path]
  --format pretty|json|yaml|mermaid|svg
  --service <name>        # show detail for specific service
  --deps <name>           # show dependencies
  --blast-radius <nodeId> # show change impact
```

## Testing

- TopologyServiceTest — test service detection, connection mapping, all query methods
- TopologyControllerTest — MockMvc tests for all REST endpoints
- MCP tools integration tests
- Benchmark: run on spring-boot, nest, eShop (multi-service projects)

## What Changes

| Component | Change |
|---|---|
| `NodeKind.java` | Add SERVICE |
| `Analyzer.java` | Run ServiceDetector after linkers |
| `TopologyService.java` | New service |
| `TopologyController.java` | New REST controller |
| `McpTools.java` | 10 new tools, 1 enhanced |
| `FlowViews.java` | New topology view |
| `StatsService.java` | Add service category |
| `.osscodeiq.yml` | Topology config section |

## What Doesn't Change

- Existing detectors, parsers, ANTLR, JavaParser
- Existing graph model (nodes, edges) — we ADD to it
- Existing MCP tools — unchanged
- Existing REST API — unchanged
- Existing CLI commands — unchanged
