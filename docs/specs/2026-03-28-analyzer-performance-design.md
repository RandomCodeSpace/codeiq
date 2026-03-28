# Analyzer Performance & Resource Optimization — Design Spec

**Date:** 2026-03-28
**Status:** Draft — needs deeper research on query layer before implementation
**Target:** 50K files, 4-5M LOC, K8s runner (800m CPU / 4GB RAM)

## Context

Current analyzer takes 10-15 min on 8-core / 32GB server. Target environment is a GitLab K8s runner with 800m CPU / 4GB RAM. Analysis runs once per release (every 3-4 weeks), full scan only. Query performance is critical — triage queries must return in milliseconds.

## Approved Design Sections

### 1. Batched Streaming Pipeline

Replace all-or-nothing pipeline with batch-based processing.

**Current flow:**
```
discover ALL files → list[Path]
    → parse+detect ALL files → list[DetectorResult]  (50K results in memory)
        → flush ALL nodes → flush ALL edges  (peak memory)
            → run linkers → classify
```

**Proposed flow:**
```
discover files (generator)
    → worker picks file, parse+detect
        → accumulator collects results
            → every batch_size files (default 500): flush nodes+edges to backend
                → after all batches: flush deferred edges → linkers → classify
```

**Data safety — two-pass edge handling:**
- Per batch: flush nodes immediately, flush edges where both endpoints exist
- Edges with missing targets → `deferred_edges` queue (small — only cross-batch refs)
- After ALL batches: flush deferred edges (all nodes now exist)
- Final assertion: `total_nodes_flushed == sum(batch_node_counts)`
- On batch flush failure: raise immediately, no silent partial state

**Config:** `analysis.batch_size: 500` in `.osscodeiq.yml`

**Memory profile (50K files, ~100K nodes):**

| | Current | Batched |
|---|---|---|
| Pending nodes | ~100K | ~1K (500 files worth) |
| Pending edges | ~150K | ~1.5K + small deferred queue |
| Results list | 50K tuples | Gone — processed per batch |
| Peak RAM | 400-500MB | 200-250MB |

### 2. Adaptive Parallelism

Auto-detect available CPU and scale workers accordingly.

```python
def _effective_workers() -> int:
    try:
        available = len(os.sched_getaffinity(0))  # respects cgroup/K8s limits
    except AttributeError:
        available = os.cpu_count() or 1
    return max(1, available - 1)
```

| Environment | CPU | Workers | Parser Pool |
|---|---|---|---|
| K8s runner (800m) | 1 | 1 (serial, no ThreadPoolExecutor) | 1 |
| Dev server (8000m) | 8 | 7 | 7 |
| Laptop (4 cores) | 4 | 3 | 3 |

- Parser pool size always matches worker count
- Config override (`analysis.parallelism`) still wins if set explicitly
- On 1 worker: pure sequential loop, zero threading overhead

### 3. Skip Cache for Full Scan

- New `--no-cache` CLI flag on `analyze` and `bundle`
- When set: no `CacheStore`, no `cache.db`, no 50K `is_cached()` calls
- `bundle` defaults to `--no-cache` (always full scan)
- Eliminates all SQLite cache overhead on CI runners

### 4. SQLite as Default Backend

- `GraphConfig.backend` default: `"networkx"` → `"sqlite"`
- `bundle` command default: `"kuzu"` → `"sqlite"`
- SQLite path defaults to `.osscodeiq/graph.db`
- NetworkX remains available for in-memory server use

### 5. SQLite Bulk Insert

Add `bulk_add_nodes()` / `bulk_add_edges()` to SQLite backend:

- Single transaction per batch (`BEGIN` → `executemany` → `COMMIT`)
- Rollback on failure — no partial state
- Edge validation via in-memory `seen_node_ids: set[str]` instead of per-edge `has_node()` query

| | Before (per-row commit) | After (batch transaction) |
|---|---|---|
| Nodes (100K) | 100K commits | ~100 commits (1 per batch) |
| Edges (150K) | 150K commits + 150K `has_node` pairs | ~100 commits, in-memory validation |
| Disk syncs | ~250K | ~200 |

### 6. Bundle Enhancement

**New bundle contents:**
```
project-v1.0-codegraph.zip
├── manifest.json          # metadata + git SHA + counts
├── graph/graph.db         # SQLite knowledge graph
├── flow.html              # interactive visualization
└── source/                # full source tree (via git ls-files)
    ├── src/
    ├── tests/
    └── ...
```

- Source inclusion via `git ls-files` (respects .gitignore)
- Excludes `.git/` and `.osscodeiq/` directories
- Manifest includes: tag, backend, git_sha, git_branch, node/edge counts, file counts, timestamp

### 7. Registry Pre-cache (Minor)

Pre-build `dict[str, list[Detector]]` in `DetectorRegistry` at load time. `detectors_for_language()` becomes O(1) lookup instead of O(115) list comprehension × 50K calls.

## Needs More Research (Post-MVP)

### SQLite Query Layer Optimization

Current problems identified but not yet designed:

1. **Full table scans** — `all_nodes()`, `all_edges()` load everything into memory
2. **NetworkX fallback** — `shortest_path()` and `find_cycles()` call `_to_networkx()` which loads the entire graph into RAM (~150MB spike, 2-5s)
3. **Missing indexes** — no index on `file_path`, `module`, `fqn`, `label`
4. **No denormalized columns** — key fields buried in JSON blob, can't be indexed
5. **No FTS** — `/api/search?q=` requires full scan + JSON parse
6. **Deep traversal** — recursive CTE explodes at depth 7-10 (branching factor 3-5)

**Preliminary ideas (need validation):**
- Denormalize `file_path`, `module`, `fqn` into real columns
- Add FTS5 virtual table for text search
- Hybrid query strategy: SQL CTE for depth 1-5, targeted subgraph + NetworkX for depth 6-10
- Cap traversal depth at 10
- Cache `_to_networkx()` result if multiple graph algorithms needed in same request

**DB size estimate at 100K nodes / 180K edges: 120-200MB uncompressed**

These optimizations are critical for triage query performance (millisecond targets) but need profiling with real data before committing to a design.

## What This Does NOT Change

- Linkers still run sequentially after all batches (need full graph)
- Layer classifier runs after linkers
- Server architecture unchanged
- MCP tools unchanged
- All 2,074 existing tests must pass
