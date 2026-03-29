package io.github.randomcodespace.iq.detector.iac;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class TerraformDetectorTest {
    private final TerraformDetector d = new TerraformDetector();
    @Test void detectsResource() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("terraform", "resource \"aws_instance\" \"web\" {\n  ami = \"ami-123\"\n}"));
        assertEquals(1, r.nodes().size()); assertEquals(NodeKind.INFRA_RESOURCE, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("terraform", "# comment")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("terraform", "resource \"aws_s3_bucket\" \"b\" {}\nvariable \"name\" {}")); }
}
