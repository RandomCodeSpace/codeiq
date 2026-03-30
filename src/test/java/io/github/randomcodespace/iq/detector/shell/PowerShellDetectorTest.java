package io.github.randomcodespace.iq.detector.shell;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class PowerShellDetectorTest {
    private final PowerShellDetector d = new PowerShellDetector();
    @Test void detectsFunction() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("powershell", "function Get-Users {\n  param()\n}"));
        assertTrue(r.nodes().size() >= 1); assertEquals(NodeKind.METHOD, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("powershell", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("powershell", "function Do-Thing {\n  Import-Module Az\n}")); }
}
