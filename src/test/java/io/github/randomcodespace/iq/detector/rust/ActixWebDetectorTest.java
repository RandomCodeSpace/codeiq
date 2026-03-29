package io.github.randomcodespace.iq.detector.rust;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ActixWebDetectorTest {
    private final ActixWebDetector d = new ActixWebDetector();
    @Test void detectsActixRoute() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("rust", "#[get(\"/hello\")]\nasync fn hello() -> impl Responder {}"));
        assertTrue(r.nodes().size() >= 1); assertEquals(NodeKind.ENDPOINT, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("rust", "fn main() {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("rust", "#[get(\"/a\")]\nasync fn a() {}\n#[post(\"/b\")]\nasync fn b() {}")); }
}
