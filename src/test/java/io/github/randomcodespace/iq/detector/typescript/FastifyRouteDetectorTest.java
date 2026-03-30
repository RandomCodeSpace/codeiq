package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastifyRouteDetectorTest {

    private final FastifyRouteDetector detector = new FastifyRouteDetector();

    @Test
    void detectsShorthandRoutes() {
        String code = """
                fastify.get('/api/users', async (request, reply) => {});
                fastify.post('/api/users', async (request, reply) => {});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(0).getKind());
        assertEquals("GET /api/users", result.nodes().get(0).getLabel());
        assertEquals("fastify", result.nodes().get(0).getProperties().get("framework"));
    }

    @Test
    void detectsHooks() {
        String code = """
                fastify.addHook('onRequest', async (request, reply) => {});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.MIDDLEWARE, result.nodes().get(0).getKind());
        assertEquals("hook:onRequest", result.nodes().get(0).getLabel());
    }

    @Test
    void noMatchOnNonFastifyCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "fastify.get('/test', handler);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
