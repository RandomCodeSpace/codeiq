package io.github.randomcodespace.iq.detector.frontend;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class FrontendRouteDetectorTest {
    private final FrontendRouteDetector d = new FrontendRouteDetector();
    @Test void detectsReactRoute() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("typescript", "<Route path=\"/home\" component={Home}>"));
        assertTrue(r.nodes().size() >= 1); assertEquals(NodeKind.ENDPOINT, r.nodes().get(0).getKind());
    }
    @Test void detectsNextjsPages() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("pages/about.tsx", "typescript", "export default function About() {}"));
        assertEquals(1, r.nodes().size()); assertEquals("route /about", r.nodes().get(0).getLabel());
    }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("typescript", "<Route path=\"/a\" component={A}>\n<Route path=\"/b\" element={<B/>}>")); }
}
