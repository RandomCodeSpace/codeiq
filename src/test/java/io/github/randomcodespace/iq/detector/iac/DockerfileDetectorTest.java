package io.github.randomcodespace.iq.detector.iac;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class DockerfileDetectorTest {
    private final DockerfileDetector d = new DockerfileDetector();
    @Test void detectsFromAndExpose() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("dockerfile", "FROM node:18\nEXPOSE 3000\nENV NODE_ENV=production"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("dockerfile", "# comment")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("dockerfile", "FROM node:18 AS builder\nFROM nginx\nCOPY --from=builder /app /app")); }
}
