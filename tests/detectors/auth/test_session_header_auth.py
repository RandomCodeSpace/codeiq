"""Tests for session, header, API key, and CSRF authentication detector."""

from __future__ import annotations

from code_intelligence.detectors.auth.session_header_auth import SessionHeaderAuthDetector
from code_intelligence.detectors.base import DetectorContext
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, language: str, file_path: str = "test_file") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestSessionHeaderAuthDetectorMetadata:
    def test_name(self):
        d = SessionHeaderAuthDetector()
        assert d.name == "session_header_auth"

    def test_supported_languages(self):
        d = SessionHeaderAuthDetector()
        assert set(d.supported_languages) == {"java", "python", "typescript"}

    def test_unsupported_language_returns_empty(self):
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx("HttpSession session = request.getSession();", "csharp", "test.cs"))
        assert len(result.nodes) == 0


class TestSessionPatterns:
    def test_detect_express_session(self):
        code = """\
const session = require('express-session');
app.use(session({ secret: 'keyboard cat', resave: false }));
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "app.ts"))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) >= 1
        assert middleware[0].properties["auth_type"] == "session"

    def test_detect_cookie_session(self):
        code = """const cookieSession = require('cookie-session');"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "app.ts"))
        assert len(result.nodes) == 1
        assert result.nodes[0].kind == NodeKind.MIDDLEWARE
        assert result.nodes[0].properties["auth_type"] == "session"

    def test_detect_session_attributes_java(self):
        code = """\
@SessionAttributes("user")
@Controller
public class LoginController {
}
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "java", "LoginController.java"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 1
        assert guards[0].properties["auth_type"] == "session"

    def test_detect_session_middleware_python(self):
        code = """\
MIDDLEWARE = [
    'django.contrib.sessions.middleware.SessionMiddleware',
]
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) >= 1

    def test_detect_http_session_java(self):
        code = """\
public void doGet(HttpServletRequest req, HttpServletResponse resp) {
    HttpSession session = req.getSession();
}
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "java", "Servlet.java"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 1
        assert guards[0].properties["auth_type"] == "session"

    def test_detect_session_engine_django(self):
        code = 'SESSION_ENGINE = "django.contrib.sessions.backends.db"'
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        assert len(result.nodes) == 1
        assert result.nodes[0].properties["auth_type"] == "session"

    def test_session_node_id_format(self):
        code = """const session = require('express-session');"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "app.ts"))
        assert result.nodes[0].id == "auth:app.ts:session:1"


class TestHeaderPatterns:
    def test_detect_x_api_key_header(self):
        code = """\
const apiKey = req.headers['X-API-Key'];
if (!apiKey) { return res.status(401).send('Unauthorized'); }
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "middleware.ts"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 1

    def test_detect_authorization_header(self):
        code = """const token = req.headers['authorization'];"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "auth.ts"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) >= 1
        assert guards[0].properties["auth_type"] == "header"

    def test_detect_java_get_header(self):
        code = 'String auth = request.getHeader("Authorization");'
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "java", "Filter.java"))
        assert len(result.nodes) >= 1
        assert result.nodes[0].properties["auth_type"] == "header"


class TestApiKeyPatterns:
    def test_detect_api_key_validation(self):
        code = """\
def validate_api_key(key):
    return key in VALID_KEYS
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "auth.py"))
        guards = [n for n in result.nodes if n.properties.get("auth_type") == "api_key"]
        assert len(guards) >= 1

    def test_detect_req_headers_x_api_key(self):
        code = """api_key = request.headers['x-api-key']"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "views.py"))
        assert len(result.nodes) >= 1


class TestCsrfPatterns:
    def test_detect_csrf_protect_decorator(self):
        code = """\
@csrf_protect
def my_view(request):
    pass
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "views.py"))
        guards = [n for n in result.nodes if n.properties.get("auth_type") == "csrf"]
        assert len(guards) >= 1
        assert guards[0].kind == NodeKind.GUARD

    def test_detect_csrf_exempt(self):
        code = """\
@csrf_exempt
def webhook(request):
    pass
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "views.py"))
        csrf_nodes = [n for n in result.nodes if n.properties.get("auth_type") == "csrf"]
        assert len(csrf_nodes) >= 1

    def test_detect_csrf_view_middleware(self):
        code = """\
MIDDLEWARE = [
    'django.middleware.csrf.CsrfViewMiddleware',
]
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "settings.py"))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) >= 1
        assert middleware[0].properties["auth_type"] == "csrf"

    def test_detect_csurf_typescript(self):
        code = """\
const csrf = require('csurf');
app.use(csrf({ cookie: true }));
"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "app.ts"))
        csrf_nodes = [n for n in result.nodes if n.properties.get("auth_type") == "csrf"]
        assert len(csrf_nodes) >= 1
        assert csrf_nodes[0].kind == NodeKind.MIDDLEWARE

    def test_csrf_node_id_format(self):
        code = "@csrf_protect"
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "python", "views.py"))
        assert result.nodes[0].id == "auth:views.py:csrf:1"


class TestSessionHeaderStatelessDeterministic:
    def test_deterministic_results(self):
        code = """\
const session = require('express-session');
const csrf = require('csurf');
"""
        d = SessionHeaderAuthDetector()
        r1 = d.detect(_ctx(code, "typescript", "app.ts"))
        r2 = d.detect(_ctx(code, "typescript", "app.ts"))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]

    def test_no_match_returns_empty(self):
        code = "console.log('hello world');"
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "index.ts"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_one_node_per_line(self):
        # A line matching multiple patterns should only produce one node.
        code = """const apiKey = req.headers['x-api-key'];"""
        d = SessionHeaderAuthDetector()
        result = d.detect(_ctx(code, "typescript", "auth.ts"))
        assert len(result.nodes) == 1
