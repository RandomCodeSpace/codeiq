package io.github.randomcodespace.iq.detector.go;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class GoStructuresDetectorTest {
    private final GoStructuresDetector d = new GoStructuresDetector();
    @Test void detectsStructAndInterface() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("go", "package main\ntype User struct {\n}\ntype Reader interface {\n}"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("go", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("go", "package main\ntype Foo struct {\n}\nfunc Bar() {}")); }
}
