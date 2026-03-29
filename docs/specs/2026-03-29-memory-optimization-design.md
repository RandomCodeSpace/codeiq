# Memory Optimization Design

**Date:** 2026-03-29
**Status:** Approved
**Target:** 50K files on K8s 800m CPU / 4GB RAM (currently OOMs)

## Problem

Current pipeline holds all nodes/edges in ArrayList before flushing. On 10K files (spring-boot): 3.9GB peak. On 50K files: OOM.

Also: SQLite analysis cache uses JNI which pins virtual threads to platform threads, reducing parallelism.

## Three-Command Architecture

### Why: Neo4j is read-optimized, not write-optimized

Neo4j Embedded excels at graph traversal reads (Cypher, shortest path, impact trace). It's not designed for high-throughput sequential writes during indexing:
- Write amplification (indexes, WAL, relationship maintenance per insert)
- 500MB runtime overhead even when only writing
- Wastes memory on CI runners that only need to write, not query

**Solution: H2 for writes (indexing), Neo4j for reads (serving).**

### Three CLI Commands

```
code-iq index /repo     → H2 file (fast sequential writes, pure Java, 2.5MB overhead)
code-iq enrich          → H2 read → Neo4j bulk load → linkers → classify → topology
code-iq serve           → Neo4j read-only (Cypher, graph algorithms, MCP)
```

| Command | Runs on | Memory | Store | What |
|---|---|---|---|---|
| `index` | CI (800m/4GB) | ~1.5GB | H2 file only | Scan + detect + batch write to H2 |
| `enrich` | CI or dev machine | ~2-3GB | H2 read → Neo4j write | Bulk load, linkers, classify, topology |
| `serve` | K8s pods (HPA) | ~1-2GB | Neo4j read-only | REST + MCP + UI, instant startup |

### Phase 1: H2 as Primary Store During Indexing

Replace in-memory ArrayLists with H2 file-based storage during `index`:
- Remove `sqlite-jdbc` dependency
- Add `h2` dependency (already in Spring Boot BOM)
- H2 schema: files, nodes, edges, analysis_runs tables
- Pure Java — no JNI, no virtual thread pinning
- MVCC concurrency — no `synchronized` blocks needed
- Batched writes: flush every 500 files to H2 (not in-memory lists)
- File path: `.code-intelligence/index.mv.db`

**Batch flush to H2:**
```
Discover files → batch 500 → analyze → INSERT nodes/edges to H2 → release memory → next batch
```

Peak memory: ~200MB per batch + 50MB H2 overhead = **under 1.5GB total**.

### Phase 2: Enrich Command (H2 → Neo4j)

Separate command that:
1. Opens H2 file (read-only)
2. Starts Neo4j Embedded
3. Bulk-loads all nodes from H2 → Neo4j (batch INSERT, no per-row overhead)
4. Bulk-loads all edges from H2 → Neo4j
5. Runs linkers (TopicLinker, EntityLinker, ModuleContainmentLinker) — these need graph traversal, Neo4j excels here
6. Runs LayerClassifier
7. Runs ServiceDetector (topology)
8. Creates Neo4j indexes for fast queries
9. Shuts down, produces `graph.db/` directory

**Why separate:** Linkers need graph traversal (find all topics, match producers to consumers). H2 is a relational DB — it can't do graph traversal efficiently. Neo4j can.

### Phase 3: Serve (Neo4j Read-Only)

`serve` loads pre-enriched `graph.db/`:
- No indexing, no enrichment on startup
- Instant startup — mount graph.db and go
- HPA scales freely — all pods mount same graph.db from PVC/S3
- Hazelcast caches query results across pods

### CI Pipeline

```bash
# On CI runner (800m/4GB)
code-iq index /repo --no-cache          # Produces: .code-intelligence/index.mv.db
code-iq enrich                           # Produces: .osscodeiq/graph.db/

# Bundle for artifact
code-iq bundle --tag v1.0               # ZIP: graph.db + source + flow.html

# On triage server (K8s, HPA)
code-iq serve --graph /path/to/graph.db  # Instant startup, read-only
```

### Memory Profile (50K files)

| Phase | Component | Memory |
|---|---|---|
| **index** | JVM + Spring (no web) | 400MB |
| | H2 file store | 50MB |
| | Batch buffer (500 files) | 200MB |
| | ANTLR/JavaParser (ThreadLocal) | 100MB |
| | Virtual thread stacks | 100MB |
| | **Total** | **~850MB** |
| **enrich** | JVM + Spring | 400MB |
| | Neo4j Embedded | 500MB |
| | H2 reader | 50MB |
| | Linker working set | 500MB |
| | **Total** | **~1.5GB** |
| **serve** | JVM + Spring (web) | 500MB |
| | Neo4j Embedded (read-only) | 500MB |
| | Hazelcast cache | 200MB |
| | **Total** | **~1.2GB** |

**All three phases fit comfortably in 4GB.**

### Data Integrity

- `index`: Every node/edge written to H2 in batch transactions. Rollback on failure.
- `enrich`: Bulk load with count assertion — H2 node count == Neo4j node count after load.
- `serve`: Read-only — no data mutation possible.
- No silent data loss — exception stops pipeline on any write failure.

### Configuration

```yaml
codeiq:
  analysis:
    batch-size: 500    # files per H2 flush batch
  index:
    store-path: .code-intelligence/index.mv.db
  graph:
    path: .osscodeiq/graph.db
```

CLI: `--batch-size 500` on index command.

## What Doesn't Change

- Detectors, parsers, ANTLR, JavaParser — same
- File discovery — same
- Virtual threads — same (better with H2)
- Determinism — same
- CLI output — same
- Incremental cache logic — same (just H2 instead of SQLite)
