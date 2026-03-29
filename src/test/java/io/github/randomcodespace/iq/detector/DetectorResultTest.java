package io.github.randomcodespace.iq.detector;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectorResultTest {

    @Test
    void emptyReturnsNoNodesOrEdges() {
        DetectorResult result = DetectorResult.empty();

        assertTrue(result.nodes().isEmpty());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void ofWithSampleData() {
        var node = new CodeNode("id1", NodeKind.CLASS, "MyClass");
        var result = DetectorResult.of(List.of(node), List.of());

        assertEquals(1, result.nodes().size());
        assertEquals("id1", result.nodes().getFirst().getId());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void listsAreImmutable() {
        var nodes = new ArrayList<CodeNode>();
        nodes.add(new CodeNode("id1", NodeKind.CLASS, "MyClass"));
        var edges = new ArrayList<CodeEdge>();

        DetectorResult result = DetectorResult.of(nodes, edges);

        assertThrows(UnsupportedOperationException.class, () -> result.nodes().add(new CodeNode()));
        assertThrows(UnsupportedOperationException.class, () -> result.edges().add(new CodeEdge()));
    }

    @Test
    void mutatingOriginalListDoesNotAffectResult() {
        var nodes = new ArrayList<CodeNode>();
        nodes.add(new CodeNode("id1", NodeKind.CLASS, "MyClass"));
        var edges = new ArrayList<CodeEdge>();

        DetectorResult result = DetectorResult.of(nodes, edges);
        nodes.add(new CodeNode("id2", NodeKind.METHOD, "doStuff"));

        assertEquals(1, result.nodes().size(), "Result should not be affected by mutation of original list");
    }
}
