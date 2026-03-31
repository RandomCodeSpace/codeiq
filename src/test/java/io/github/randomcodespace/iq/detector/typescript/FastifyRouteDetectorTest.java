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
                import Fastify from 'fastify';
                const fastify = Fastify();
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
                import Fastify from 'fastify';
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
    void noMatchOnExpressCode() {
        String code = """
                const express = require('express');
                const router = express.Router();
                router.get('/api/users', (req, res) => {});
                router.post('/api/users', (req, res) => {});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.js", "javascript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty(), "Fastify detector should not match Express routes");
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void matchesWithRequireFastify() {
        String code = """
                const fastify = require('fastify')();
                fastify.get('/health', async () => ({ status: 'ok' }));
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/server.js", "javascript", code);
        DetectorResult result = detector.detect(ctx);
        assertEquals(1, result.nodes().size());
        assertEquals("fastify", result.nodes().get(0).getProperties().get("framework"));
    }

    @Test
    void deterministic() {
        String code = """
                import Fastify from 'fastify';
                fastify.get('/test', handler);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
