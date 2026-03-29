package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlaskRouteDetectorTest {

    private final FlaskRouteDetector detector = new FlaskRouteDetector();

    @Test
    void detectsSimpleRoute() {
        String code = """
                @app.route('/hello')
                def hello():
                    return 'Hello'
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(0).getKind());
        assertEquals("GET /hello", result.nodes().get(0).getLabel());
        assertEquals("flask", result.nodes().get(0).getProperties().get("framework"));
        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.EXPOSES, result.edges().get(0).getKind());
    }

    @Test
    void detectsRouteWithMethods() {
        String code = """
                @app.route('/items', methods=['GET', 'POST'])
                def items():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getLabel().equals("GET /items")));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getLabel().equals("POST /items")));
    }

    @Test
    void noMatchOnNonRoute() {
        String code = """
                def hello():
                    return 'world'
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                @app.route('/hello')
                def hello():
                    return 'Hello'

                @bp.route('/items', methods=['GET', 'POST'])
                def items():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
