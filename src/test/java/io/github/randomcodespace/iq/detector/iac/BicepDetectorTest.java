package io.github.randomcodespace.iq.detector.iac;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class BicepDetectorTest {
    private final BicepDetector d = new BicepDetector();
    @Test void detectsAzureResource() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("bicep", "resource storageAccount 'Microsoft.Storage/storageAccounts@2021-02-01'"));
        assertEquals(1, r.nodes().size()); assertEquals(NodeKind.AZURE_RESOURCE, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("bicep", "// comment")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("bicep", "resource sa 'Microsoft.Storage/storageAccounts@2021-02-01'\nparam name string")); }
}
