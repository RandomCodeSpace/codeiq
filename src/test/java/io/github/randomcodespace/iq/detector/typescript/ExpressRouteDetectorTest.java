package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsAllHttpMethods() {
        String code = """
                app.get('/items', getAll);
                app.post('/items', create);
                app.put('/items/:id', update);
                app.patch('/items/:id', patch);
                app.delete('/items/:id', remove);
                app.options('/items', options);
                app.head('/items', head);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/items.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(7, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("GET /items"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("PUT /items/:id"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("PATCH /items/:id"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("DELETE /items/:id"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("OPTIONS /items"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().equals("HEAD /items"));
    }

    @Test
    void detectsRouterRoutes() {
        String code = """
                const router = express.Router();
                router.get('/health', checkHealth);
                router.post('/login', login);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/router.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertEquals("router", result.nodes().get(0).getProperties().get("router"));
        assertEquals("GET /health", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsRoutesWithDoubleQuotes() {
        String code = """
                app.get("/api/data", getData);
                app.post("/api/data", postData);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertEquals("GET /api/data", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsRoutesWithPathParams() {
        String code = """
                app.get('/users/:userId/posts/:postId', getPost);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("GET /users/:userId/posts/:postId", result.nodes().get(0).getLabel());
        assertEquals("/users/:userId/posts/:postId", result.nodes().get(0).getProperties().get("path_pattern"));
    }

    @Test
    void setsProtocolToREST() {
        String code = "app.get('/ping', handler);";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("REST", result.nodes().get(0).getProperties().get("protocol"));
        assertEquals("GET", result.nodes().get(0).getProperties().get("http_method"));
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
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noEdgesReturned() {
        String code = "app.get('/test', handler);";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "app.get('/test', handler);\nrouter.post('/data', fn);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.express_routes", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
