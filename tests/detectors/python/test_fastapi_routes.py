"""Tests for FastAPI route detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.fastapi_routes import FastAPIRouteDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, path: str = "main.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestFastAPIRouteDetector:
    def setup_method(self):
        self.detector = FastAPIRouteDetector()

    def test_detects_app_get(self):
        source = """\
from fastapi import FastAPI
app = FastAPI()

@app.get('/users')
def list_users():
    return []
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path_pattern"] == "/users"
        assert endpoints[0].properties["framework"] == "fastapi"

    def test_detects_app_post(self):
        source = """\
@app.post('/users')
def create_user(user: UserCreate):
    return user
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "POST"

    def test_detects_async_routes(self):
        source = """\
@app.get('/items')
async def list_items():
    return await get_items()
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1

    def test_detects_router_with_prefix(self):
        source = """\
from fastapi import APIRouter
router = APIRouter(prefix="/api/v1/users")

@router.get('/list')
def list_users():
    return []

@router.post('/create')
def create_user():
    return {}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        paths = {n.properties["path_pattern"] for n in endpoints}
        assert "/api/v1/users/list" in paths
        assert "/api/v1/users/create" in paths

    def test_detects_multiple_methods(self):
        source = """\
@app.get('/items')
def list_items():
    return []

@app.post('/items')
def create_item(item: Item):
    return item

@app.put('/items/{id}')
def update_item(id: int):
    return {}

@app.delete('/items/{id}')
def delete_item(id: int):
    pass
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 4
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "PUT", "DELETE"}

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0

    def test_no_route_decorators(self):
        source = """\
def helper_function():
    return "not a route"
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@app.get('/a')
def route_a():
    return 'a'

@app.post('/b')
def route_b():
    return 'b'
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
