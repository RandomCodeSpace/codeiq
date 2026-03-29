package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PythonStructuresDetectorTest {

    private final PythonStructuresDetector detector = new PythonStructuresDetector();

    @Test
    void detectsClassAndMethod() {
        String code = """
                class MyClass(Base):
                    def my_method(self):
                        pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertTrue(result.nodes().stream().anyMatch(
                n -> n.getKind() == NodeKind.CLASS && n.getLabel().equals("MyClass")));
        assertTrue(result.nodes().stream().anyMatch(
                n -> n.getKind() == NodeKind.METHOD && n.getLabel().equals("MyClass.my_method")));
        // EXTENDS edge + DEFINES edge
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.EXTENDS));
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.DEFINES));
    }

    @Test
    void detectsTopLevelFunction() {
        String code = """
                def my_func():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.METHOD, result.nodes().get(0).getKind());
        assertEquals("my_func", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsImports() {
        String code = """
                from os.path import join
                import sys, json
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        // 3 import edges: os.path, sys, json
        assertEquals(3, result.edges().size());
        assertTrue(result.edges().stream().allMatch(e -> e.getKind() == EdgeKind.IMPORTS));
    }

    @Test
    void detectsAllExports() {
        String code = """
                __all__ = ['foo', 'Bar']

                def foo():
                    pass

                class Bar:
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        // module node + foo function + Bar class
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.MODULE));
        var fooNode = result.nodes().stream()
                .filter(n -> n.getLabel().equals("foo"))
                .findFirst().orElseThrow();
        assertEquals(true, fooNode.getProperties().get("exported"));
    }

    @Test
    void detectsAsyncFunction() {
        String code = """
                async def fetch_data():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(true, result.nodes().get(0).getProperties().get("async"));
    }

    @Test
    void noMatchOnEmptyFile() {
        String code = "";
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
        assertEquals(0, result.edges().size());
    }

    @Test
    void deterministic() {
        String code = """
                from os import path
                import sys

                class MyClass(Base):
                    def method_a(self):
                        pass

                def standalone():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
