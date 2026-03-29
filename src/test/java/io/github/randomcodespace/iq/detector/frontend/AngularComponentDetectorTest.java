package io.github.randomcodespace.iq.detector.frontend;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class AngularComponentDetectorTest {
    private final AngularComponentDetector d = new AngularComponentDetector();
    @Test void detectsComponent() {
        String code = "@Component({\n  selector: 'app-root'\n})\nexport class AppComponent {}";
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("typescript", code));
        assertEquals(1, r.nodes().size()); assertEquals("angular", r.nodes().get(0).getProperties().get("framework"));
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("typescript", "class Foo {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("typescript", "@Component({\n  selector: 'app-root'\n})\nclass AppComponent {}")); }
}
