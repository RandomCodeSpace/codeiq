package io.github.randomcodespace.iq.detector.kotlin;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class KtorRouteDetectorTest {
    private final KtorRouteDetector d = new KtorRouteDetector();
    @Test void detectsKtorRoute() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("kotlin", "routing {\n  get(\"/hello\") {\n    call.respond(\"hi\")\n  }\n}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("kotlin", "fun main() {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("kotlin", "routing {\n  get(\"/a\") {}\n  post(\"/b\") {}\n}")); }
}
