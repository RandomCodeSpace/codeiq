package io.github.randomcodespace.iq.detector.csharp;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class CSharpEfcoreDetectorTest {
    private final CSharpEfcoreDetector d = new CSharpEfcoreDetector();
    @Test void detectsDbContext() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("csharp", "public class AppDbContext : DbContext\n{\n  public DbSet<User> Users { get; set; }\n}"));
        assertTrue(r.nodes().size() >= 2);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("csharp", "class Foo {}")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("csharp", "class AppCtx : DbContext { DbSet<User> Users {} }")); }
}
