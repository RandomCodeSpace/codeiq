package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DjangoModelDetectorTest {

    private final DjangoModelDetector detector = new DjangoModelDetector();

    @Test
    void detectsDjangoModel() {
        String code = """
                class User(models.Model):
                    name = models.CharField(max_length=100)
                    email = models.EmailField()
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("models.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        var entityNode = result.nodes().stream().filter(n -> n.getKind() == NodeKind.ENTITY).findFirst().orElseThrow();
        assertEquals("User", entityNode.getLabel());
        assertEquals("django", entityNode.getProperties().get("framework"));
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.CONNECTS_TO));
    }

    @Test
    void detectsForeignKeyRelationship() {
        String code = """
                class Order(models.Model):
                    user = models.ForeignKey("User", on_delete=models.CASCADE)
                    total = models.DecimalField()
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("models.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        assertEquals(2, result.edges().size()); // DEPENDS_ON + CONNECTS_TO
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.DEPENDS_ON));
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.CONNECTS_TO));
    }

    @Test
    void detectsManager() {
        String code = """
                class ActiveManager(models.Manager):
                    pass

                class Item(models.Model):
                    name = models.CharField(max_length=50)
                    objects = ActiveManager()
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("models.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        // 1 manager + 1 model + 1 database:unknown = 3 nodes
        assertEquals(3, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.REPOSITORY));
        // manager assignment edge
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.QUERIES));
    }

    @Test
    void noMatchOnPlainClass() {
        String code = """
                class MyService:
                    def do_thing(self):
                        pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                class User(models.Model):
                    name = models.CharField(max_length=100)

                class Order(models.Model):
                    user = models.ForeignKey("User", on_delete=models.CASCADE)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("models.py", "python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
