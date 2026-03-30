package io.github.randomcodespace.iq.detector.rust;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class RustStructuresDetectorTest {
    private final RustStructuresDetector d = new RustStructuresDetector();
    @Test void detectsStructAndTrait() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("rust", "pub struct User {}\npub trait Serialize {}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("rust", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("rust", "struct A {}\ntrait B {}\nfn c() {}")); }
}
