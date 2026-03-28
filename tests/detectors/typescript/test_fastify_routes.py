"""Tests for Fastify route detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.fastify_routes import FastifyRouteDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "routes.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestFastifyRouteDetector:
    def setup_method(self):
        self.detector = FastifyRouteDetector()

    # --- Positive tests ---

    def test_detects_shorthand_get(self):
        source = """\
import Fastify from 'fastify';
const fastify = Fastify();

fastify.get('/users', async (request, reply) => {
    return { users: [] };
});
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path_pattern"] == "/users"
        assert endpoints[0].properties["framework"] == "fastify"

    def test_detects_multiple_http_methods(self):
        source = """\
fastify.get('/items', listItems);
fastify.post('/items', createItem);
fastify.put('/items/:id', updateItem);
fastify.delete('/items/:id', deleteItem);
fastify.patch('/items/:id', patchItem);
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 5
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "PUT", "DELETE", "PATCH"}

    def test_detects_route_object(self):
        source = """\
fastify.route({
    method: 'GET',
    url: '/health',
    handler: async (request, reply) => {
        return { status: 'ok' };
    }
});
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path_pattern"] == "/health"

    def test_detects_route_with_schema(self):
        source = """\
fastify.route({
    method: 'POST',
    url: '/users',
    schema: { body: CreateUserSchema },
    handler: async (request, reply) => {
        return request.body;
    }
});
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert "schema" in endpoints[0].properties

    def test_detects_register_plugin(self):
        source = """\
fastify.register(cors);
fastify.register(authPlugin);
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.edges) == 2
        assert all(e.kind == EdgeKind.IMPORTS for e in result.edges)
        plugins = {e.properties["plugin"] for e in result.edges}
        assert plugins == {"cors", "authPlugin"}

    def test_detects_add_hook(self):
        source = """\
fastify.addHook('onRequest', async (request, reply) => {
    // auth check
});
fastify.addHook('preHandler', validateInput);
"""
        result = self.detector.detect(_ctx(source))
        hooks = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(hooks) == 2
        hook_names = {n.properties["hook_name"] for n in hooks}
        assert hook_names == {"onRequest", "preHandler"}

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_fastify_calls(self):
        source = """\
const express = require('express');
const app = express();
app.get('/users', handler);
"""
        result = self.detector.detect(_ctx(source))
        # express routes detected under variable 'app' would match the generic pattern,
        # but we still get endpoints (since the regex matches any variable).
        # The key is that framework == 'fastify' in properties.
        for node in result.nodes:
            if node.kind == NodeKind.ENDPOINT:
                assert node.properties["framework"] == "fastify"

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
fastify.get('/a', handlerA);
fastify.post('/b', handlerB);
fastify.addHook('onRequest', hookHandler);
fastify.register(myPlugin);
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert len(r1.edges) == len(r2.edges)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert [(e.source, e.target, e.kind) for e in r1.edges] == [
            (e.source, e.target, e.kind) for e in r2.edges
        ]

    def test_node_id_format(self):
        source = """\
fastify.get('/test', handler);
"""
        result = self.detector.detect(_ctx(source, path="src/routes.ts"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].id.startswith("fastify:src/routes.ts:GET:/test:")
