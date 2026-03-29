package io.github.randomcodespace.iq.detector.csharp;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class CSharpStructuresDetectorTest {
    private final CSharpStructuresDetector d = new CSharpStructuresDetector();
    @Test void detectsClassAndNamespace() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("csharp", "namespace MyApp\n{\n  public class UserService {}\n}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("csharp", "")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("csharp", "namespace X { public class A {} public interface IB {} }")); }
}
