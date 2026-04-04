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

    @Test
    void detectsBlueprintRoute() {
        String code = """
                @bp.route('/users')
                def list_users():
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("GET /users", result.nodes().get(0).getLabel());
        assertEquals("bp", result.nodes().get(0).getProperties().get("blueprint"));
    }

    @Test
    void routeHasProtocolRest() {
        String code = """
                @app.route('/api/data')
                def data():
                    return {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("REST", result.nodes().get(0).getProperties().get("protocol"));
    }

    @Test
    void routeHasHttpMethodProperty() {
        String code = """
                @app.route('/submit', methods=['POST'])
                def submit():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("POST", result.nodes().get(0).getProperties().get("http_method"));
    }

    @Test
    void routeHasPathPattern() {
        String code = """
                @app.route('/user/<int:id>')
                def user(id):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("/user/<int:id>", result.nodes().get(0).getProperties().get("path_pattern"));
    }

    @Test
    void exposesEdgeSourceIsBlueprint() {
        String code = """
                @app.route('/ping')
                def ping():
                    return 'pong'
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        var edge = result.edges().get(0);
        assertTrue(edge.getSourceId().contains("app"));
    }

    @Test
    void multipleMethodsGenerateMultipleNodes() {
        String code = """
                @api.route('/resource', methods=['GET', 'PUT', 'DELETE'])
                def resource():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertEquals(3, result.edges().size());
    }

    @Test
    void noMatchOnEmptyContent() {
        DetectorContext ctx = DetectorTestUtils.contextFor("python", "");
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
        assertEquals(0, result.edges().size());
    }

    @Test
    void fqnIncludesFunctionName() {
        String code = """
                @app.route('/health')
                def health_check():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("api.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertNotNull(result.nodes().get(0).getFqn());
        assertTrue(result.nodes().get(0).getFqn().contains("health_check"));
    }

    @Test
    void defaultMethodIsGet() {
        String code = """
                @app.route('/list')
                def list_items():
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("GET", result.nodes().get(0).getProperties().get("http_method"));
    }
}
