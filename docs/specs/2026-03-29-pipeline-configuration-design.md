# Pipeline Configuration & Discovery Design

**Date:** 2026-03-29
**Status:** Approved
**Scope:** Config-driven predictable pipeline, detector metadata annotation, CLI discovery commands

## Problem

The current pipeline auto-detects everything at runtime — CPU cores, languages, parsers, minified files, modules. Each auto-detect costs CPU cycles. For CI pipelines that scan the same repo repeatedly, this is wasted work.

Users who know their repo should be able to configure exactly what runs, skipping everything irrelevant.

## Principle

**If the user KNOWS, let them tell us. If they don't, auto-detect.**

Config-driven mode = maximum CPU efficiency. No config = backward compatible auto-detect.

## .osscodeiq.yml — Pipeline Configuration

```yaml
# Pipeline performance tuning
pipeline:
  parallelism: 2                # Fixed thread count (default: auto-detect from CPU)
  batch-size: 500               # Files per H2 flush batch (default: 500)

# Only scan these languages — everything else skipped at file discovery
# If omitted: auto-detect from file extensions (current behavior)
languages:
  - java
  - kotlin
  - typescript
  - yaml
  - json

# Only run these detector categories
# If omitted: run all detectors (current behavior)
detectors:
  categories:
    - endpoints
    - entities
    - auth
    - config
    - infra
  # OR explicit detector names:
  # include:
  #   - java/spring_rest
  #   - java/jpa_entity
  #   - python/fastapi_routes

# Explicit parser assignment — no fallback chains
# If omitted: auto-select (JavaParser for java, ANTLR for python/go/etc, regex for rest)
parsers:
  java: javaparser
  typescript: regex
  python: antlr
  go: antlr
  yaml: structured

# Explicit excludes — no runtime heuristics
exclude:
  - "**/*.min.js"
  - "**/*.bundle.js"
  - "**/*.chunk.js"
  - "**/node_modules/**"
  - "**/build/**"
  - "**/dist/**"
  - "**/vendor/**"
  - "**/.git/**"

# Topology connections (for service topology feature)
topology:
  connections:
    - CALLS
    - PRODUCES
    - CONSUMES
    - QUERIES
    - CONNECTS_TO
```

## What Each Config Controls

### `languages` — File Discovery Filter

```
Without config:  50K files → all enter pipeline → extension mapping per file
With config:     50K files → filter to configured extensions → 30K enter pipeline
```
20K files never read, never hashed, never passed to detectors.

### `detectors.categories` — Detector Filter

```
Without config:  97 detectors instantiated, all run per file
With config:     12 detectors instantiated, only relevant ones run
```
85 detector invocations skipped per file. On 30K files = 2.5M skipped calls.

### `parsers` — Parser Assignment

```
Without config:  Try ANTLR → parse fails → fall back to regex (CPU wasted on failed parse)
With config:     Use assigned parser directly, no fallback chain
```
No double-parsing. Every parse attempt succeeds or doesn't happen.

### `pipeline.parallelism` — Thread Count

```
Without config:  Runtime.getRuntime().availableProcessors() (auto-detect)
With config:     Fixed value, no detection overhead
```

### `exclude` — Skip Patterns

```
Without config:  Read file → check if minified (scan content) → maybe skip
With config:     Match glob at file discovery → skip immediately, never read
```
No content scanning for minified detection on excluded files.

## @DetectorInfo Annotation

Single source of truth for detector metadata. Every detector declares what it does:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DetectorInfo {
    String name();
    String category();
    String description();
    ParserType parser() default ParserType.REGEX;
    String[] languages();
    NodeKind[] nodeKinds();
    EdgeKind[] edgeKinds() default {};
    String[] properties() default {};
}

public enum ParserType {
    REGEX,
    JAVAPARSER,
    ANTLR,
    STRUCTURED
}
```

Usage:
```java
@Component
@DetectorInfo(
    name = "spring_rest",
    category = "endpoints",
    description = "Detects Spring MVC REST endpoints (@GetMapping, @PostMapping, etc.)",
    parser = ParserType.JAVAPARSER,
    languages = {"java"},
    nodeKinds = {NodeKind.ENDPOINT},
    edgeKinds = {EdgeKind.EXPOSES},
    properties = {"http_method", "path", "produces", "consumes", "framework"}
)
public class SpringRestDetector extends AbstractJavaParserDetector { ... }
```

## Detector Categories

| Category | What it finds | Detectors |
|---|---|---|
| `endpoints` | REST, gRPC, WebSocket, GraphQL endpoints | spring_rest, fastapi_routes, express_routes, nestjs_controllers, jaxrs, etc. |
| `entities` | Database entities, ORM models | jpa_entity, sqlalchemy_models, typeorm_entities, mongoose_orm, django_models, etc. |
| `auth` | Authentication and authorization | spring_security, fastapi_auth, nestjs_guards, passport_jwt, certificate_auth, ldap_auth, etc. |
| `messaging` | Kafka, RabbitMQ, JMS topics and consumers | kafka, kafka_js, rabbitmq, jms, celery_tasks, etc. |
| `config` | Configuration files, infrastructure | kubernetes, helm_chart, github_actions, gitlab_ci, docker_compose, terraform, etc. |
| `infra` | Infrastructure resources | dockerfile, bicep, cloudformation, etc. |
| `structures` | Classes, interfaces, methods, imports | class_hierarchy, python_structures, typescript_structures, go_structures, etc. |
| `frontend` | UI components and routes | react_components, vue_components, angular_components, frontend_routes, etc. |
| `database` | Database connections, queries | jdbc, raw_sql, cosmos_db, go_orm, efcore, etc. |

## CLI Discovery Commands

### `code-iq plugins list`

```
Category     Detectors  Description
─────────────────────────────────────────────────────
endpoints    14         REST, gRPC, WebSocket, GraphQL endpoints
entities     10         Database entities, ORM models
auth          8         Authentication and authorization
messaging     7         Kafka, RabbitMQ, JMS, Celery
config       18         K8s, Helm, GHA, GitLab CI, CloudFormation
infra         4         Dockerfile, Terraform, Bicep
structures   12         Classes, interfaces, methods, imports
frontend      6         React, Vue, Angular, Svelte components
database      8         JDBC, SQL, Cosmos DB, Go ORM, EF Core

Total: 97 detectors across 9 categories
```

### `code-iq plugins info <category>`

```
$ code-iq plugins info endpoints

Category: endpoints — REST, gRPC, WebSocket, GraphQL endpoints

  spring_rest          Java     JavaParser  Spring MVC @GetMapping, @PostMapping, etc.
  spring_events        Java     JavaParser  Spring @EventListener publishers/subscribers
  jaxrs                Java     Regex       JAX-RS @Path, @GET, @POST annotations
  grpc_service         Java     Regex       gRPC service definitions and stubs
  websocket            Java     Regex       Spring WebSocket @MessageMapping
  fastapi_routes       Python   ANTLR       FastAPI @app.get(), @router.post() decorators
  flask_routes         Python   ANTLR       Flask @app.route() decorators
  django_views         Python   ANTLR       Django URL patterns and class-based views
  express_routes       TS/JS    Regex       Express router.get(), app.post() calls
  nestjs_controllers   TS       Regex       NestJS @Controller, @Get, @Post decorators
  fastify_routes       TS/JS    Regex       Fastify route handlers
  remix_routes         TS/JS    Regex       Remix loader/action exports
  graphql_resolvers    TS/JS    Regex       GraphQL @Resolver, @Query, @Mutation
  actix_web            Rust     Regex       Actix-web #[get], #[post] macros
```

### `code-iq plugins info <category/name>`

```
$ code-iq plugins info endpoints/spring_rest

Name:        spring_rest
Category:    endpoints
Parser:      JavaParser AST (regex fallback)
Languages:   java
Description: Detects Spring MVC REST endpoints from @RequestMapping,
             @GetMapping, @PostMapping, @PutMapping, @DeleteMapping,
             @PatchMapping annotations. Extracts HTTP method, path,
             produces/consumes media types, and parameter annotations.

Node kinds:  ENDPOINT
Edge kinds:  EXPOSES
Properties:  http_method, path, produces, consumes, framework, router

Example output:
  Node: endpoint:src/main/.../UserController.java:GET:/api/users
  Label: GET /api/users
  Properties: {http_method: GET, path: /api/users, framework: spring}
```

### `code-iq plugins languages`

```
Language      Extensions              Detectors  Parser
──────────────────────────────────────────────────────────
java          .java                   28         JavaParser
typescript    .ts, .tsx               13         Regex (ANTLR planned)
python        .py                     11         ANTLR
config/yaml   .yaml, .yml            10         Structured (SnakeYAML)
config/json   .json                   5         Structured (Jackson)
go            .go                      4         ANTLR
csharp        .cs                      4         ANTLR
rust          .rs                      3         ANTLR
kotlin        .kt, .kts               3         ANTLR
shell         .sh, .bash, .ps1        3         Regex
scala         .scala                   2         ANTLR
cpp           .cpp, .cc, .h           2         ANTLR
terraform     .tf, .tfvars            2         Regex
dockerfile    Dockerfile              1         Regex
markdown      .md                      1         Regex
proto         .proto                   1         Regex
xml           .xml, .pom              1         Structured (JAXB)
```

### `code-iq plugins suggest <path>` — The Killer Feature

Scans repo, analyzes file distribution, generates optimized config:

```
$ code-iq plugins suggest /path/to/my-enterprise-app

🔍 Scanning /path/to/my-enterprise-app ...
📁 Found 15,234 files

Language distribution:
  java         8,432 files (55%)
  typescript   3,201 files (21%)
  yaml           892 files  (6%)
  json           567 files  (4%)
  kotlin         423 files  (3%)
  properties     312 files  (2%)
  other        1,407 files  (9%) — would be skipped

Suggested .osscodeiq.yml:
──────────────────────────
pipeline:
  parallelism: 4
  batch-size: 500

languages:
  - java
  - typescript
  - kotlin
  - yaml
  - json
  - properties

detectors:
  categories:
    - endpoints
    - entities
    - auth
    - messaging
    - config
    - structures

exclude:
  - "**/node_modules/**"
  - "**/build/**"
  - "**/dist/**"
  - "**/*.min.js"

# Estimated: ~13,827 files analyzed (9% skipped)
# Estimated: ~85 detectors active (12 skipped)

Save to .osscodeiq.yml? [Y/n]
```

### `code-iq plugins docs --format markdown`

Auto-generates full reference documentation from `@DetectorInfo` annotations:

```bash
code-iq plugins docs --format markdown > docs/detector-reference.md
code-iq plugins docs --format json > docs/detector-reference.json
code-iq plugins docs --format yaml > docs/detector-reference.yaml
```

Output: complete detector catalog with categories, descriptions, languages, node/edge kinds, properties. Always matches code — single source of truth.

## Implementation

### DetectorInfo annotation + DetectorRegistry enhancement

1. Create `@DetectorInfo` annotation
2. Add annotation to all 97 detectors
3. Enhance `DetectorRegistry` to read annotations at startup
4. Build category index: `Map<String, List<Detector>>`

### Config-driven pipeline in Analyzer

1. Read `.osscodeiq.yml` at analysis start
2. If `languages` configured → filter file discovery
3. If `detectors.categories` configured → filter detector registry
4. If `parsers` configured → override parser selection per language
5. If `pipeline.parallelism` configured → use fixed thread count
6. If `exclude` configured → apply glob patterns at file discovery

### Enhanced plugins CLI command

1. `list` — reads from DetectorRegistry category index
2. `info` — reads @DetectorInfo annotation for detail
3. `languages` — aggregates from all detectors' language declarations
4. `suggest` — quick file scan + language stats + config generation
5. `docs` — generates full reference doc from annotations

### Testing

- PluginsCommandTest — test all subcommands
- Config-driven pipeline test — verify filtering works
- Suggest command test — verify config generation

## What Changes

| Component | Change |
|---|---|
| `@DetectorInfo` | New annotation |
| All 97 detectors | Add @DetectorInfo annotation |
| `DetectorRegistry` | Read annotations, build category index |
| `Analyzer` | Config-driven filtering (languages, detectors, parsers) |
| `FileDiscovery` | Language + exclude filtering from config |
| `PluginsCommand` | Enhanced: list, info, languages, suggest, docs |
| `.osscodeiq.yml` | New pipeline/detectors/parsers/exclude sections |

## What Doesn't Change

- Detector logic — same detection, just filtered
- Graph model — same nodes/edges
- MCP tools — same
- REST API — same
- Serve — same
