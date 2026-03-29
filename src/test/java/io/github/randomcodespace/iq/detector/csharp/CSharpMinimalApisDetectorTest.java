package io.github.randomcodespace.iq.detector.csharp;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class CSharpMinimalApisDetectorTest {
    private final CSharpMinimalApisDetector d = new CSharpMinimalApisDetector();
    @Test void detectsMapGet() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("csharp", "var app = WebApplication.CreateBuilder(args);\napp.MapGet(\"/hello\", () => \"Hello\");"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("csharp", "class Foo {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("csharp", "WebApplication.CreateBuilder(args);\napp.MapGet(\"/a\", h);\napp.MapPost(\"/b\", h);")); }
}
