package io.github.randomcodespace.iq.detector.go;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class GoOrmDetectorTest {
    private final GoOrmDetector d = new GoOrmDetector();
    @Test void detectsGormModel() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("go", "import \"gorm.io/gorm\"\ntype User struct {\n  gorm.Model\n}"));
        assertTrue(r.nodes().size() >= 1); assertEquals(NodeKind.ENTITY, r.nodes().get(0).getKind());
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("go", "package main")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("go", "import \"gorm.io/gorm\"\ntype User struct {\n  gorm.Model\n}\ndb.AutoMigrate(&User{})")); }
}
