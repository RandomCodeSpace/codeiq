package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExpressRouteDetectorTest {

    private final ExpressRouteDetector detector = new ExpressRouteDetector();

    @Test
    void detectsExpressRoutes() {
        String code = """
                const app = express();
                app.get('/api/users', getUsers);
                app.post('/api/users', createUser);
                router.delete('/api/users/:id', deleteUser);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(0).getKind());
        assertEquals("GET /api/users", result.nodes().get(0).getLabel());
        assertEquals("POST /api/users", result.nodes().get(1).getLabel());
        assertEquals("express", result.nodes().get(0).getProperties().get("framework"));
        assertEquals("app", result.nodes().get(0).getProperties().get("router"));
    }

    @Test
    void noMatchOnNonExpressCode() {
        String code = """
                const x = 42;
                console.log('hello');
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "app.get('/test', handler);\nrouter.post('/data', fn);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
