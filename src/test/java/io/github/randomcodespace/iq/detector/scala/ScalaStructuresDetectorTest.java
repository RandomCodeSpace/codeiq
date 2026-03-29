package io.github.randomcodespace.iq.detector.scala;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ScalaStructuresDetectorTest {
    private final ScalaStructuresDetector d = new ScalaStructuresDetector();
    @Test void detectsClassAndTrait() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("scala", "class User extends Entity\ntrait Serializable\ndef process(x: Int) = x"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("scala", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("scala", "case class Foo(x: Int)\nobject Bar")); }
}
