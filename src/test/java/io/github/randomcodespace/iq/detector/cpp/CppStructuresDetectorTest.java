package io.github.randomcodespace.iq.detector.cpp;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class CppStructuresDetectorTest {
    private final CppStructuresDetector d = new CppStructuresDetector();
    @Test void detectsClassAndNamespace() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("cpp", "#include <string>\nnamespace app {\nclass User : public Entity {\n};\n}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("cpp", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("cpp", "#include <vector>\nclass A {\n};\nstruct B {\n};")); }
}
