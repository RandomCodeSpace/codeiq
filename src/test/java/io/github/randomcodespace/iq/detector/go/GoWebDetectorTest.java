package io.github.randomcodespace.iq.detector.go;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class GoWebDetectorTest {
    private final GoWebDetector d = new GoWebDetector();
    @Test void detectsGinRoute() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("go", "r := gin.Default()\nr.GET(\"/users\", getUsers)"));
        assertTrue(r.nodes().size() >= 1); assertEquals(NodeKind.ENDPOINT, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("go", "package main")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("go", "r := gin.Default()\nr.GET(\"/a\", a)\nr.POST(\"/b\", b)")); }
}
