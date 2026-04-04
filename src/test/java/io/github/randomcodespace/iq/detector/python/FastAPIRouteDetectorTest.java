package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastAPIRouteDetectorTest {

    private final FastAPIRouteDetector detector = new FastAPIRouteDetector();

    @Test
    void detectsGetRoute() {
        String code = """
                @app.get("/items")
                async def list_items():
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(0).getKind());
        assertEquals("GET /items", result.nodes().get(0).getLabel());
        assertEquals("fastapi", result.nodes().get(0).getProperties().get("framework"));
    }

    @Test
    void detectsPostRoute() {
        String code = """
                @router.post("/items")
                async def create_item(item: Item):
                    return item
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("POST /items", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsRouteWithPrefix() {
        String code = """
                router = APIRouter(prefix="/api/v1")

                @router.get("/users")
                def list_users():
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("GET /api/v1/users", result.nodes().get(0).getLabel());
    }

    @Test
    void noMatchOnPlainFunction() {
        String code = """
                def get_users():
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                @app.get("/items")
                async def list_items():
                    return []

                @app.post("/items")
                async def create_item():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void detectsPutRoute() {
        String code = """
                @router.put("/items/{id}")
                async def update_item(id: int):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("PUT /items/{id}", result.nodes().get(0).getLabel());
        assertEquals("PUT", result.nodes().get(0).getProperties().get("http_method"));
    }

    @Test
    void detectsDeleteRoute() {
        String code = """
                @app.delete("/items/{id}")
                async def delete_item(id: int):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("DELETE /items/{id}", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsPatchRoute() {
        String code = """
                @app.patch("/items/{id}")
                async def patch_item(id: int):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("PATCH /items/{id}", result.nodes().get(0).getLabel());
    }

    @Test
    void routeHasProtocolRest() {
        String code = """
                @app.get("/health")
                def health():
                    return {"status": "ok"}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("REST", result.nodes().get(0).getProperties().get("protocol"));
    }

    @Test
    void routeHasRouterName() {
        String code = """
                @myrouter.get("/api/data")
                def get_data():
                    return {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("myrouter", result.nodes().get(0).getProperties().get("router"));
    }

    @Test
    void routeWithPrefixCombinesFullPath() {
        String code = """
                router = APIRouter(prefix="/v2")

                @router.post("/articles")
                def create_article():
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("POST /v2/articles", result.nodes().get(0).getLabel());
        assertEquals("/v2/articles", result.nodes().get(0).getProperties().get("path_pattern"));
    }

    @Test
    void noMatchOnEmptyContent() {
        DetectorContext ctx = DetectorTestUtils.contextFor("python", "");
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void fqnIncludesFunctionName() {
        String code = """
                @app.get("/ping")
                def ping():
                    return "pong"
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("routes.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertNotNull(result.nodes().get(0).getFqn());
        assertTrue(result.nodes().get(0).getFqn().contains("ping"));
    }

    @Test
    void multipleRoutes() {
        String code = """
                @app.get("/a")
                async def route_a(): pass

                @app.post("/b")
                async def route_b(): pass

                @app.delete("/c")
                async def route_c(): pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
    }
}
