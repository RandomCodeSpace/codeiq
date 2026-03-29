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
}
