"""Tests for Flask route detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.flask_routes import FlaskRouteDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "app.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestFlaskRouteDetector:
    def setup_method(self):
        self.detector = FlaskRouteDetector()

    def test_detects_app_route(self):
        source = """\
from flask import Flask
app = Flask(__name__)

@app.route('/users')
def list_users():
    return jsonify(users)
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 1
        assert endpoints[0].properties["path_pattern"] == "/users"
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["framework"] == "flask"

    def test_detects_route_with_methods(self):
        source = """\
@app.route('/users', methods=['GET', 'POST'])
def handle_users():
    pass
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST"}

    def test_detects_blueprint_route(self):
        source = """\
from flask import Blueprint
users_bp = Blueprint('users', __name__)

@users_bp.route('/profile')
def profile():
    return render_template('profile.html')
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 1
        assert endpoints[0].properties["blueprint"] == "users_bp"

    def test_creates_exposes_edges(self):
        source = """\
@app.route('/health')
def health_check():
    return {'status': 'ok'}
"""
        result = self.detector.detect(_ctx(source))
        expose_edges = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(expose_edges) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_route_decorator(self):
        source = """\
def helper_function():
    return "not a route"
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@app.route('/items')
def list_items():
    return []

@app.route('/items/<id>')
def get_item(id):
    return {}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
