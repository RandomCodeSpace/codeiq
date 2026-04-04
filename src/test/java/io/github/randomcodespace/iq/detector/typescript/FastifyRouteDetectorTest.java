package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsAllHttpMethodsShorthand() {
        String code = """
                import Fastify from 'fastify';
                fastify.get('/items', h);
                fastify.post('/items', h);
                fastify.put('/items/:id', h);
                fastify.patch('/items/:id', h);
                fastify.delete('/items/:id', h);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/items.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(5, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "GET /items".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "DELETE /items/:id".equals(n.getLabel()));
        assertThat(result.nodes()).allMatch(n -> n.getKind() == NodeKind.ENDPOINT);
    }

    @Test
    void detectsRouteObjectStyle() {
        String code = """
                import Fastify from 'fastify';
                fastify.route({
                  method: 'GET',
                  url: '/api/health',
                  handler: async (request, reply) => ({ status: 'ok' })
                });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/health.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertThat(result.nodes()).anyMatch(n -> "GET /api/health".equals(n.getLabel()));
    }

    @Test
    void detectsHooks() {
        String code = """
                import Fastify from 'fastify';
                fastify.addHook('onRequest', async (request, reply) => {});
                fastify.addHook('preHandler', async (request, reply) => {});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertThat(result.nodes()).allMatch(n -> n.getKind() == NodeKind.MIDDLEWARE);
        assertThat(result.nodes()).anyMatch(n -> "hook:onRequest".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "hook:preHandler".equals(n.getLabel()));
    }

    @Test
    void detectsPluginRegistrationEdges() {
        String code = """
                import Fastify from 'fastify';
                import authPlugin from './auth';
                fastify.register(authPlugin);
                fastify.register(corsPlugin);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.edges().isEmpty());
        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.IMPORTS);
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
    void endpointNodeHasProtocol() {
        String code = """
                import Fastify from 'fastify';
                fastify.get('/data', handler);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("REST", result.nodes().get(0).getProperties().get("protocol"));
        assertEquals("GET", result.nodes().get(0).getProperties().get("http_method"));
    }

    @Test
    void detectsWithNamedImport() {
        String code = """
                import { fastify } from 'fastify';
                fastify.get('/status', statusHandler);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertFalse(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = """
                import Fastify from 'fastify';
                fastify.get('/test', handler);
                fastify.post('/test', handler);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("fastify_routes", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
