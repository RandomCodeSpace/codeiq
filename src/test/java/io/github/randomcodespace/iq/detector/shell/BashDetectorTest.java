package io.github.randomcodespace.iq.detector.shell;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class BashDetectorTest {
    private final BashDetector d = new BashDetector();
    @Test void detectsFunction() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("bash", "#!/bin/bash\nfunction deploy() {\n  docker build .\n}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("bash", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("bash", "#!/bin/bash\nfunction a() {\n  echo hi\n}")); }
}
