package io.github.randomcodespace.iq.detector.kotlin;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class KotlinStructuresDetectorTest {
    private final KotlinStructuresDetector d = new KotlinStructuresDetector();
    @Test void detectsClassAndInterface() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("kotlin", "class User\ninterface Repo\nfun findAll() {}"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("kotlin", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("kotlin", "data class Foo(val x: Int)\nobject Bar")); }
}
