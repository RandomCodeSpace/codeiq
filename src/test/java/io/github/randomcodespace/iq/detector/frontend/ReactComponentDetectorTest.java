package io.github.randomcodespace.iq.detector.frontend;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ReactComponentDetectorTest {
    private final ReactComponentDetector d = new ReactComponentDetector();
    @Test void detectsFunctionComponent() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("typescript", "export default function MyApp() {\n  return <div/>;\n}"));
        assertEquals(1, r.nodes().size()); assertEquals(NodeKind.COMPONENT, r.nodes().get(0).getKind()); assertEquals("MyApp", r.nodes().get(0).getLabel());
    }
    @Test void noMatchOnPlainCode() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("typescript", "function lowercase() {}"));
        assertEquals(0, r.nodes().size());
    }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("typescript", "export default function App() {}\nexport function useAuth() {}")); }
}
