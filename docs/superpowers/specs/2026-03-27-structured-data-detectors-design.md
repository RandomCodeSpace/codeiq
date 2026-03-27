# Structured Data Detectors Design

**Date:** 2026-03-27
**Status:** Approved

## Problem

The code-intelligence tool discovers ~12,728 files (JSON, YAML, TOML, Markdown, INI, Proto, SQL, Batch) that are parsed but produce zero detection nodes. Additionally ~53 files are silently dropped due to missing `include_extensions` entries.

## Solution

Add 16 new detectors using a layered approach: generic structure detectors as baseline, plus specialized sub-detectors for well-known config files.

## Architecture

### Layered Detection Strategy

Each format gets two layers:
1. **Generic detector** — extracts top-level structure as `CONFIG_KEY`/`CONFIG_FILE` nodes for any file of that format
2. **Specialized sub-detectors** — triggered by filename pattern matching (e.g., `package.json`, `docker-compose.yml`); return empty `DetectorResult` for non-matching files

All detectors follow the existing `Detector` protocol:
- `name: str` — unique detector name
- `supported_languages: tuple[str, ...]` — language strings from `_EXTENSION_MAP`
- `detect(ctx: DetectorContext) -> DetectorResult` — pure function, no side effects

Detectors use `ctx.parsed_data` (already populated by existing JSON/YAML parsers) or `ctx.content` for regex-based formats (Proto, Batch, Markdown).

### Detectors

#### Generic (6 detectors)

| # | Detector | File | Languages | Extracts |
|---|---|---|---|---|
| 1 | `json_structure` | `config/json_structure.py` | `json` | CONFIG_FILE node per file, CONFIG_KEY nodes for top-level keys |
| 2 | `yaml_structure` | `config/yaml_structure.py` | `yaml` | CONFIG_FILE node per file, CONFIG_KEY nodes for top-level keys, multi-doc support |
| 3 | `toml_structure` | `config/toml_structure.py` | `toml` | CONFIG_FILE node per file, CONFIG_KEY for `[sections]` and top-level keys |
| 4 | `ini_structure` | `config/ini_structure.py` | `ini` | CONFIG_FILE node per file, CONFIG_KEY for `[sections]` and keys |
| 5 | `markdown_structure` | `docs/markdown_structure.py` | `markdown` | MODULE node per file, heading hierarchy, links as DEPENDS_ON edges |
| 6 | `proto_structure` | `proto/proto_structure.py` | `proto` | INTERFACE for services, METHOD for RPCs, PROTOCOL_MESSAGE for messages, IMPORTS edges for imports |

#### Specialized JSON (3 detectors)

| # | Detector | File | Trigger | Extracts |
|---|---|---|---|---|
| 7 | `package_json` | `config/package_json.py` | filename == `package.json` | MODULE node for package, DEPENDS_ON edges for dependencies/devDependencies, METHOD nodes for scripts |
| 8 | `tsconfig_json` | `config/tsconfig_json.py` | filename == `tsconfig.json` or `tsconfig.*.json` | CONFIG_FILE node, DEPENDS_ON edges for `references[].path` and `extends` |
| 9 | `openapi` | `config/openapi.py` | `json`, `yaml` — presence of `openapi` or `swagger` key in parsed data | ENDPOINT nodes for paths+methods, ENTITY nodes for schema definitions, DEPENDS_ON for $ref references |

#### Specialized YAML (3 detectors)

| # | Detector | File | Trigger | Extracts |
|---|---|---|---|---|
| 10 | `docker_compose` | `config/docker_compose.py` | filename matches `docker-compose*.yml` or `compose*.yml` or has `services` key | INFRA_RESOURCE for services, DEPENDS_ON for depends_on/links, CONFIG_KEY for ports/volumes/networks |
| 11 | `github_actions` | `config/github_actions.py` | path contains `.github/workflows/` | MODULE for workflow, METHOD for jobs, CONFIG_KEY for triggers (on:), DEPENDS_ON for job needs |
| 12 | `kubernetes` | `config/kubernetes.py` | `kind` key with known K8s types (Deployment, Service, ConfigMap, Ingress, Pod, StatefulSet, DaemonSet, Job, CronJob) | INFRA_RESOURCE for resources, CONFIG_KEY for container specs, DEPENDS_ON for service selectors |

#### Specialized TOML (1 detector)

| # | Detector | File | Trigger | Extracts |
|---|---|---|---|---|
| 13 | `pyproject_toml` | `config/pyproject_toml.py` | filename == `pyproject.toml` | MODULE for project, DEPENDS_ON for dependencies, CONFIG_DEFINITION for entry points/scripts |

#### Other Formats (3 detectors)

| # | Detector | File | Languages | Extracts |
|---|---|---|---|---|
| 14 | `sql_structure` | `config/sql_structure.py` | `sql` | ENTITY for CREATE TABLE, CONFIG_DEFINITION for indexes/constraints, DEPENDS_ON for foreign keys |
| 15 | `batch_structure` | `config/batch_structure.py` | `batch` | MODULE for script, METHOD for labels (:label), CALLS edges for CALL commands, CONFIG_DEFINITION for SET variables |
| 16 | `properties_detector` | `config/properties_detector.py` | `properties` | CONFIG_FILE node, CONFIG_KEY for each property, detect Spring profiles and DB connection strings |

### File Organization

```
src/code_intelligence/detectors/
├── config/                        # NEW
│   ├── __init__.py
│   ├── json_structure.py          # Generic JSON
│   ├── yaml_structure.py          # Generic YAML
│   ├── toml_structure.py          # Generic TOML
│   ├── ini_structure.py           # Generic INI
│   ├── package_json.py            # npm package.json
│   ├── tsconfig_json.py           # TypeScript config
│   ├── openapi.py                 # OpenAPI/Swagger (JSON + YAML)
│   ├── docker_compose.py          # Docker Compose
│   ├── github_actions.py          # GitHub Actions workflows
│   ├── kubernetes.py              # K8s manifests
│   ├── pyproject_toml.py          # Python pyproject.toml
│   ├── sql_structure.py           # SQL DDL
│   ├── batch_structure.py         # Batch scripts
│   └── properties_detector.py     # Java properties
├── docs/                          # NEW
│   ├── __init__.py
│   └── markdown_structure.py      # Markdown docs
└── proto/                         # NEW
    ├── __init__.py
    └── proto_structure.py         # Protocol Buffers
```

### Node ID Strategy

Consistent ID format for cross-file linking:
- Generic: `"{format}:{filepath}"` for file nodes, `"{format}:{filepath}:{key}"` for key nodes
- Specialized: `"npm:{filepath}:{package_name}"`, `"k8s:{filepath}:{kind}:{name}"`, etc.
- Proto: `"proto:{filepath}:{message_name}"`, `"proto:{filepath}:{service_name}"`

### Registry Changes

Add all 16 modules to `load_builtin_detectors()` in `registry.py`:
```python
"code_intelligence.detectors.config.json_structure",
"code_intelligence.detectors.config.yaml_structure",
"code_intelligence.detectors.config.toml_structure",
"code_intelligence.detectors.config.ini_structure",
"code_intelligence.detectors.config.package_json",
"code_intelligence.detectors.config.tsconfig_json",
"code_intelligence.detectors.config.openapi",
"code_intelligence.detectors.config.docker_compose",
"code_intelligence.detectors.config.github_actions",
"code_intelligence.detectors.config.kubernetes",
"code_intelligence.detectors.config.pyproject_toml",
"code_intelligence.detectors.config.sql_structure",
"code_intelligence.detectors.config.batch_structure",
"code_intelligence.detectors.config.properties_detector",
"code_intelligence.detectors.docs.markdown_structure",
"code_intelligence.detectors.proto.proto_structure",
```

### Config Fix

Add missing extensions to `include_extensions` default list in `config.py`:
- `.env`
- `.csv`
- `.dockerfile`

(`.graphql`, `.gql`, `.jsx`, `.tfvars` are already in the list per latest config check)

### TOML/INI Parser Addition

The `_parse_structured()` function in `analyzer.py` needs handlers for `toml` and `ini` languages:
- TOML: Use Python 3.11+ `tomllib` (stdlib) or `tomli` fallback
- INI: Use `configparser` from stdlib
- Properties: Already has parser

### Existing Node/Edge Kinds Used

No new enum values needed. All detectors use existing kinds:
- `NodeKind`: MODULE, METHOD, ENDPOINT, ENTITY, CONFIG_FILE, CONFIG_KEY, CONFIG_DEFINITION, INFRA_RESOURCE, INTERFACE, PROTOCOL_MESSAGE
- `EdgeKind`: DEPENDS_ON, IMPORTS, CALLS, CONTAINS, DEFINES, CONNECTS_TO

### Testing Strategy

Each detector gets a unit test with fixture files in `tests/fixtures/`. Test that:
1. Correct node types and counts are produced
2. Correct edge relationships are created
3. Specialized detectors return empty results for non-matching files
4. Generic detectors work on any file of their format
5. Parsers handle malformed input gracefully

### Performance Considerations

- Generic detectors limit CONFIG_KEY extraction to top-level keys (not recursive) to avoid node explosion on large JSON/YAML files
- Specialized detectors do filename check first and bail early
- All detectors are stateless and thread-safe
