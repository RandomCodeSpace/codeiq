package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CeleryTaskDetectorTest {

    private final CeleryTaskDetector detector = new CeleryTaskDetector();

    @Test
    void detectsTaskDefinition() {
        String code = """
                @app.task
                def send_email(to, subject):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("tasks.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.QUEUE));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.METHOD));
        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.CONSUMES, result.edges().get(0).getKind());
    }

    @Test
    void detectsTaskWithExplicitName() {
        String code = """
                @shared_task(name='emails.send')
                def send_email(to, subject):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("tasks.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        var queueNode = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.QUEUE)
                .findFirst().orElseThrow();
        assertEquals("celery:emails.send", queueNode.getLabel());
    }

    @Test
    void detectsTaskInvocation() {
        String code = """
                result = send_email.delay("user@test.com", "Hello")
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("views.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.PRODUCES, result.edges().get(0).getKind());
    }

    @Test
    void noMatchOnPlainFunction() {
        String code = """
                def send_email(to, subject):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
        assertEquals(0, result.edges().size());
    }

    @Test
    void deterministic() {
        String code = """
                @app.task
                def process_data(data):
                    pass

                result = process_data.delay(42)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("tasks.py", "python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
