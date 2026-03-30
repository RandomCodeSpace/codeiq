package io.github.randomcodespace.iq.detector;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;

import java.util.List;

public record DetectorResult(List<CodeNode> nodes, List<CodeEdge> edges) {
    public DetectorResult {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public static DetectorResult empty() {
        return new DetectorResult(List.of(), List.of());
    }

    public static DetectorResult of(List<CodeNode> nodes, List<CodeEdge> edges) {
        return new DetectorResult(nodes, edges);
    }
}
