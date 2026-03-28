# Code Intelligence — Project Instructions

## What This Project Is

A CLI tool that scans codebases to build a deterministic code knowledge graph. No AI, no external APIs — pure pattern matching. 72 detectors, 35 languages, 3 storage backends (NetworkX, SQLite, KuzuDB).

## Architecture

```
FileDiscovery → Parsers → Detectors → GraphBuilder (buffered) → Linkers → LayerClassifier → GraphStore (backend)
```

- **Detectors** follow the `Detector` Protocol in `detectors/base.py` — implement `name`, `supported_languages`, `detect(ctx) -> DetectorResult`
- **Backends** follow the `GraphBackend` Protocol in `graph/backend.py` — implement 16 methods. `CypherBackend` is optional for Cypher-capable backends.
- **GraphStore** is a facade delegating to a backend — never access backends directly
- **GraphBuilder** buffers all nodes and edges, flushes nodes first then edges (ensures cross-backend parity)
- **Linkers** run after all detectors, produce cross-file relationship edges
- **LayerClassifier** runs after linkers, sets `layer` property on every node

## Critical Rules

### Determinism is Non-Negotiable
- Same input MUST produce same output, every time, on every backend
- No set iteration without `sorted()` first
- No dependency on thread completion order (builder uses indexed result slots)
- All detectors must be stateless pure functions — no class-level mutable state
- Benchmark after every change: run 2+ times, assert identical node/edge counts

### Cross-Backend Data Parity
- All 3 backends (NetworkX, SQLite, KuzuDB) must produce identical node and edge counts
- Edges are only added if both source and target nodes exist
- Test parity after any change to builder, store, or backends

### Adding a New Detector
1. Create file in `detectors/<category>/my_detector.py`
2. Implement `Detector` protocol (name, supported_languages, detect method)
3. Add to the hardcoded list in `detectors/registry.py` (will be auto-discovered after tech debt cleanup)
4. Create test in `tests/detectors/<category>/test_my_detector.py`
5. Include a determinism test (run twice, assert identical output)
6. Run `pytest tests/ -x -q` — all tests must pass

### Adding a New Backend
1. Create file in `graph/backends/my_backend.py`
2. Implement `GraphBackend` protocol (16 methods)
3. Optionally implement `CypherBackend` for Cypher support
4. Add to factory in `graph/backends/__init__.py`
5. Add to `GraphConfig` backend choices in `config.py`
6. Test parity: same nodes/edges as NetworkX on the same input

## Code Conventions

- Python 3.11+, `from __future__ import annotations`
- Pydantic for data models, typer for CLI, rich for output
- Regex-based detection (no tree-sitter dependency for new detectors unless needed)
- `NodeKind` and `EdgeKind` enums in `models/graph.py` — add new values there
- ID format: `"{prefix}:{filepath}:{type}:{identifier}"` for cross-file uniqueness
- Properties dict for detector-specific metadata (`auth_type`, `framework`, `roles`, etc.)
- `layer` property on every node: `frontend | backend | infra | shared | unknown`

## Testing

- `pytest tests/ -x -q` — must always pass (currently 361 tests)
- Every detector needs: positive match test, negative match test, determinism test
- Benchmark on spring-boot (10K files) for performance regression checks
- Cross-backend parity test on contoso-real-estate for data quality

## Key Files

| File | Purpose |
|------|---------|
| `detectors/base.py` | Detector protocol (42 lines) |
| `graph/backend.py` | GraphBackend + CypherBackend protocols |
| `graph/store.py` | GraphStore facade |
| `graph/builder.py` | GraphBuilder with buffered flush + linkers |
| `graph/backends/networkx.py` | Default in-memory backend |
| `graph/backends/kuzu.py` | KuzuDB embedded graph DB with Cypher |
| `graph/backends/sqlite_backend.py` | SQLite file-based backend |
| `classifiers/layer_classifier.py` | Deterministic layer classification |
| `models/graph.py` | NodeKind, EdgeKind, GraphNode, GraphEdge |
| `config.py` | Config with GraphConfig for backend selection |
| `analyzer.py` | Pipeline orchestrator |
| `cli.py` | CLI commands (analyze, graph, query, find, cypher, bundle, cache, plugins) |

## Known Tech Debt (Phase 2)

- Registry has 75-entry hardcoded detector list — needs auto-discovery
- `imports_detector.py` is 723 lines — needs splitting per language
- 60+ detectors have no tests — need coverage
- `_parse_structured()` has 11-branch elif chain — needs dispatch table
- Linker protocol uses `_new_module_nodes` private attribute hack — needs `LinkResult`
- Missing extensions: `.html`, `.css`, `.mjs`, `.cjs`, `.jsonc`, `.groovy`, `.pyi`
- No extensionless file support (Dockerfile, Makefile, go.mod)

## Updating This File

After significant changes (new detectors, new backends, architectural decisions, conventions learned), update this CLAUDE.md to reflect the current state. Keep it concise and actionable.
