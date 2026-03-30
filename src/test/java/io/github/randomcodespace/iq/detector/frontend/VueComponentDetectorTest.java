package io.github.randomcodespace.iq.detector.frontend;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class VueComponentDetectorTest {
    private final VueComponentDetector d = new VueComponentDetector();
    @Test void detectsOptionsApi() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("javascript", "export default { name: 'MyComp' }"));
        assertEquals(1, r.nodes().size()); assertEquals("MyComp", r.nodes().get(0).getLabel());
    }
    @Test void noMatch() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("javascript", "const x = 1;"));
        assertEquals(0, r.nodes().size());
    }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("javascript", "export default { name: 'Comp' }")); }
}
