# Benchmark Results -- Java vs Python

Date: 2026-03-29
Machine: 4 CPU cores, 16 GB RAM
Java: 25.0.2, Spring Boot 4.0.5, ZGC, embedded Neo4j 2026.02.3
Python: 3.12.13, OSSCodeIQ 0.0.0 (main branch, NetworkX backend)

## Results

| Project | Files (Java) | Files (Python) | Python Nodes | Java Nodes | Python Edges | Java Edges | Python Time | Java Time (analysis) | Java Time (wall) | Speedup (analysis) | Consistent? |
|---------|-------------|----------------|-------------|------------|-------------|------------|-------------|---------------------|-------------------|---------------------|-------------|
| spring-boot | 10524 | 10872 | 27446 | 27987 | 32890 | 39776 | 56.8s | 47.8s avg | 66.9s avg | 1.2x | Yes (3/3) |
| kafka | 6919 | 7003 | 58080 | 62671 | 99974 | 120376 | 96.8s | 63.5s avg | 73.7s avg | 1.5x | Yes (3/3) |
| contoso-real-estate | 484 | 488 | 3844 | 4034 | 2906 | 4039 | 7.6s | 1.3s avg | 10.2s avg | 5.8x | Yes (3/3) |
| benchmark | 311284 | N/A | N/A | N/A | N/A | N/A | OOM/timeout | OOM (3GB) | N/A | N/A | N/A |

### Notes on timing
- **Java Time (analysis)**: Time reported by the Analyzer itself (excludes Spring Boot startup, Neo4j init)
- **Java Time (wall)**: Total wall clock time including JVM startup (~8-20s Spring Boot overhead)
- **Python Time**: Wall clock time (minimal startup overhead)
- **Speedup**: Based on analysis time (Java) vs wall time (Python), since Python has negligible startup

## Consistency (3 runs per project -- Java)

| Project | Run 1 (nodes/edges) | Run 2 | Run 3 | Identical? |
|---------|---------------------|-------|-------|------------|
| spring-boot | 27987 / 39776 | 27987 / 39776 | 27987 / 39776 | Yes |
| kafka | 62671 / 120376 | 62671 / 120376 | 62671 / 120376 | Yes |
| contoso-real-estate | 4034 / 4039 | 4034 / 4039 | 4034 / 4039 | Yes |

## Analysis Time Breakdown (Java, 3 runs)

| Project | Run 1 | Run 2 | Run 3 | Avg | Std Dev |
|---------|-------|-------|-------|-----|---------|
| spring-boot | 48.0s | 50.8s | 44.5s | 47.8s | 3.2s |
| kafka | 69.6s | 61.5s | 59.3s | 63.5s | 5.4s |
| contoso-real-estate | 1.37s | 1.33s | 1.28s | 1.33s | 0.04s |

## Wall Clock Time Breakdown (Java, 3 runs)

| Project | Run 1 | Run 2 | Run 3 | Avg |
|---------|-------|-------|-------|-----|
| spring-boot | 66.7s | 70.5s | 64.4s | 67.2s |
| kafka | 81.5s | 71.4s | 69.1s | 74.0s |
| contoso-real-estate | 10.5s | 10.1s | 10.0s | 10.2s |

## Node/Edge Count Differences (Java vs Python)

Java consistently finds MORE nodes and edges than Python:

| Project | Node Diff | Edge Diff | Node % | Edge % |
|---------|-----------|-----------|--------|--------|
| spring-boot | +541 | +6886 | +2.0% | +20.9% |
| kafka | +4591 | +20402 | +7.9% | +20.4% |
| contoso-real-estate | +190 | +1133 | +4.9% | +39.0% |

This indicates Java detectors are catching more patterns than the Python version.
The file count difference (Java discovers slightly fewer files) suggests different
gitignore/exclusion handling, but Java extracts more signal per file.

## CLI Output Quality

### Progress messages
- File discovery: "Discovering files..." and "Found N files" with emoji icons
- Analysis: "Analyzing N files..." with gear emoji
- Building: "Building graph..." with construction emoji
- Linking: "Linking cross-file relationships..." with link emoji
- Classifying: "Classifying layers..." with label emoji
- Completion: "Analysis complete" with checkmark emoji

### Issues observed
- **SLF4J multiple provider warning**: Two SLF4J providers on classpath (Logback + Neo4j). Cosmetic only.
- **Spring Boot banner**: Full ASCII art banner displayed on every run (~6 lines). Could suppress with `spring.main.banner-mode=off`.
- **Neo4j deprecation warnings**: `CodeEdge` uses Long IDs (deprecated). Should migrate to external IDs.
- **MCP warnings**: "No tool/resource/prompt/complete methods found" -- expected when running CLI analyze (MCP not needed for CLI).
- **XML DOCTYPE warnings**: "[Fatal Error]" lines from XML parser encountering DOCTYPE declarations. These are noisy but non-fatal.
- **Java restricted method warnings**: Netty and jctools use deprecated sun.misc.Unsafe APIs. Upstream dependency issue.
- **Spring Boot startup overhead**: 8-16s just to start the application context (Neo4j embedded, Spring Data, MCP server init) before any analysis begins.

### What's NOT shown (but should be)
- No parallelism level report (e.g., "Using virtual threads on 4 cores")
- No memory usage report at completion
- No per-detector timing breakdown

## Benchmark Project (311K files)

The benchmark project (8.8GB, 311,284 files) contains multiple large open-source repos
(TypeScript, azure-sdk-for-java, azure-sdk-for-python, django, eShop, kotlin,
kubernetes, rust-analyzer, terraform-provider-azurerm).

- **Java**: Initial run completed in ~11m40s (wall) with 3GB heap but output was lost due to piping issues. Subsequent run with 10GB heap timed out at 10 minutes (process killed).
- **Python**: Timed out at 10 minutes, peak memory 8GB+ and still growing.

Neither implementation handles 300K+ files well within reasonable time/memory bounds.
This suggests a need for incremental analysis or chunked processing for very large monorepos.

## Recommendations

1. **Suppress Spring Boot banner** for CLI commands (`spring.main.banner-mode=off` or `log` mode)
2. **Suppress MCP warnings** when running in CLI/indexing mode (not serving)
3. **Handle XML DOCTYPE gracefully** -- catch and suppress the stderr output from the XML parser
4. **Report parallelism** -- log virtual thread usage and core count at startup
5. **Investigate edge count difference** -- Java finds 20-39% more edges; verify these are real (not false positives)
6. **Add memory reporting** -- show peak heap usage at analysis completion
7. **Lazy Neo4j initialization** -- don't start embedded Neo4j for the `analyze` command if results are only in-memory
8. **Profile large codebase handling** -- 311K files needs streaming/chunked approach
