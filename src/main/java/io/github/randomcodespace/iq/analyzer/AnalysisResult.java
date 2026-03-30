package io.github.randomcodespace.iq.analyzer;

import io.github.randomcodespace.iq.model.CodeNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Result of running the full analysis pipeline.
 *
 * @param totalFiles        total files discovered
 * @param filesAnalyzed     files that were actually analyzed (detectors ran)
 * @param nodeCount         total graph nodes produced
 * @param edgeCount         total graph edges produced
 * @param languageBreakdown count of files per language
 * @param nodeBreakdown       count of nodes per NodeKind value
 * @param frameworkBreakdown  count of nodes per detected framework
 * @param elapsed             wall-clock duration of the analysis
 * @param nodes               the actual graph nodes (may be null for backward compat)
 */
public record AnalysisResult(
        int totalFiles,
        int filesAnalyzed,
        int nodeCount,
        int edgeCount,
        Map<String, Integer> languageBreakdown,
        Map<String, Integer> nodeBreakdown,
        Map<String, Integer> edgeBreakdown,
        Map<String, Integer> frameworkBreakdown,
        Duration elapsed,
        List<CodeNode> nodes
) {
    /** Backward-compatible constructor without nodes. */
    public AnalysisResult(
            int totalFiles, int filesAnalyzed, int nodeCount, int edgeCount,
            Map<String, Integer> languageBreakdown, Map<String, Integer> nodeBreakdown,
            Map<String, Integer> edgeBreakdown, Map<String, Integer> frameworkBreakdown,
            Duration elapsed) {
        this(totalFiles, filesAnalyzed, nodeCount, edgeCount,
                languageBreakdown, nodeBreakdown, edgeBreakdown, frameworkBreakdown,
                elapsed, null);
    }
}
