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

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENTITY, result.nodes().get(0).getKind());
        assertEquals("User", result.nodes().get(0).getLabel());
        assertEquals("django", result.nodes().get(0).getProperties().get("framework"));
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

        assertEquals(1, result.nodes().size());
        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.DEPENDS_ON, result.edges().get(0).getKind());
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

        // 1 manager + 1 model = 2 nodes
        assertEquals(2, result.nodes().size());
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
