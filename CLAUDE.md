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

- `pytest tests/ -x -q` — must always pass (currently 565 tests)
- Every detector needs: positive match test, negative match test, determinism test
- All detectors use shared `detectors/utils.py` — decode_text, find_line_number, etc.

## Benchmark Requirements

**After every change**, run a clean benchmark on a small project to verify:
1. No performance regression (time should not increase significantly)
2. 100% determinism (2 runs produce identical node/edge counts)
3. Coverage doesn't decrease (file/node/edge counts should not drop)

**Benchmark procedure:**
```bash
rm -rf ~/projects/testDir/contoso-real-estate/.code-intelligence/
find ~/projects/testDir/contoso-real-estate -name ".code_intelligence_cache*" -delete
# Run twice
time code-intelligence analyze ~/projects/testDir/contoso-real-estate --full -j 8
time code-intelligence analyze ~/projects/testDir/contoso-real-estate --full -j 8
```

If `testDir/contoso-real-estate` is not available, clone an official secure project:
```bash
git clone --depth 1 https://github.com/Azure-Samples/contoso-real-estate.git ~/projects/testDir/contoso-real-estate
```

**Baseline (contoso-real-estate, 488 files):** 2,313 nodes, 2,905 edges, ~3.7s
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

## Tech Debt Resolved (Phase 2 — Complete)

- Registry auto-discovers detectors via `pkgutil.walk_packages()` — new detector = create file, done
- `imports_detector.py` split into `kotlin_structures.py`, `rust_structures.py`, `scala_structures.py` with fixed regexes
- 54 new tests added for 10 previously untested detectors (415 total tests)
- `_parse_structured()` uses `_STRUCTURED_PARSERS` dispatch dict
- Linker protocol uses `LinkResult(nodes, edges)` dataclass — no more private attribute hack
- 16 new extensions added (.html, .css, .mjs, .cjs, .jsonc, .groovy, .pyi, .razor, .cshtml, .adoc, etc.)
- Extensionless files supported via `_FILENAME_MAP` (Dockerfile, Makefile, go.mod, Jenkinsfile)
- Shared `detectors/utils.py` with `decode_text`, `iter_lines`, `find_line_number`, `filename`, `matches_filename`

## Adding a New Detector (Updated)

1. Create file in `detectors/<category>/my_detector.py`
2. Implement `Detector` protocol (name, supported_languages, detect method)
3. **No registry changes needed** — auto-discovered by package scanning
4. Create test in `tests/detectors/<category>/test_my_detector.py`
5. Include a determinism test (run twice, assert identical output)
6. Run `pytest tests/ -x -q` — all tests must pass

## Remaining Work

- Phase 3: Flow generator (GitLab CI, Helm, enhanced Dockerfile, Mermaid flow command)
- Phase 4: 30+ new framework detectors (Go web, EF Core, Prisma, Pydantic, etc.)
- KuzuDB bulk import optimization for edge insertion

## Updating This File

After significant changes (new detectors, new backends, architectural decisions, conventions learned), update this CLAUDE.md to reflect the current state. Keep it concise and actionable.
