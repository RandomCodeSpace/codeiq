"""Tests for Express.js route detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.express_routes import ExpressRouteDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, path: str = "routes.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestExpressRouteDetector:
    def setup_method(self):
        self.detector = ExpressRouteDetector()

    def test_detects_app_get(self):
        source = """\
const express = require('express');
const app = express();

app.get('/users', (req, res) => {
    res.json(users);
});
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path_pattern"] == "/users"
        assert endpoints[0].properties["framework"] == "express"

    def test_detects_router_post(self):
        source = """\
const router = express.Router();

router.post('/orders', (req, res) => {
    res.status(201).json(req.body);
});
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "POST"

    def test_detects_multiple_routes(self):
        source = """\
app.get('/items', listItems);
app.post('/items', createItem);
app.put('/items/:id', updateItem);
app.delete('/items/:id', deleteItem);
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 4
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "PUT", "DELETE"}

    def test_detects_patch_route(self):
        source = """\
router.patch('/users/:id', patchUser);
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "PATCH"

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\nconsole.log(x);\n"))
        assert len(result.nodes) == 0

    def test_no_route_calls(self):
        source = """\
function helper() {
    return 'not a route';
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
app.get('/a', handlerA);
app.post('/b', handlerB);
app.put('/c', handlerC);
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
