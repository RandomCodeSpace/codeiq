"""Tests for FastAPI auth detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.fastapi_auth import FastAPIAuthDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, file_path: str = "main.py") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="python",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestFastAPIAuthDetector:
    def setup_method(self):
        self.detector = FastAPIAuthDetector()

    def test_supported_languages(self):
        assert self.detector.supported_languages == ("python",)
        assert self.detector.name == "fastapi_auth"

    def test_empty_input(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
        assert result.nodes == []
        assert result.edges == []

    def test_no_match(self):
        source = """\
from fastapi import FastAPI

app = FastAPI()

@app.get("/health")
async def health():
    return {"status": "ok"}
"""
        result = self.detector.detect(_ctx(source))
        assert result.nodes == []

    def test_depends_get_current_user(self):
        source = """\
@app.get("/me")
async def get_me(user: User = Depends(get_current_user)):
    return user
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "fastapi"
        assert node.properties["auth_flow"] == "oauth2"
        assert node.properties["dependency"] == "get_current_user"
        assert node.properties["auth_required"] is True
        assert node.id == "auth:main.py:Depends:2"

    def test_depends_get_current_active_user(self):
        source = """\
async def read_items(user = Depends(get_current_active_user)):
    return items
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["dependency"] == "get_current_active_user"

    def test_depends_require_auth(self):
        source = """\
async def protected(auth = Depends(require_auth)):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["dependency"] == "require_auth"

    def test_security_call(self):
        source = """\
@app.get("/secure")
async def secure_endpoint(token: str = Security(oauth2_scheme)):
    return {"token": token}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "fastapi"
        assert node.properties["auth_flow"] == "oauth2"
        assert node.properties["scheme"] == "oauth2_scheme"
        assert "Security" in node.id

    def test_http_bearer(self):
        source = """\
from fastapi.security import HTTPBearer

bearer_scheme = HTTPBearer()
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "fastapi"
        assert node.properties["auth_flow"] == "bearer"
        assert node.properties["auth_required"] is True
        assert node.label == "HTTPBearer()"

    def test_oauth2_password_bearer(self):
        source = """\
from fastapi.security import OAuth2PasswordBearer

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "fastapi"
        assert node.properties["auth_flow"] == "oauth2"
        assert node.properties["token_url"] == "token"
        assert "OAuth2PasswordBearer" in node.label

    def test_oauth2_password_bearer_custom_url(self):
        source = """\
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["token_url"] == "/api/v1/auth/login"

    def test_http_basic(self):
        source = """\
from fastapi.security import HTTPBasic

security = HTTPBasic()
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "fastapi"
        assert node.properties["auth_flow"] == "basic"
        assert node.properties["auth_required"] is True
        assert node.label == "HTTPBasic()"

    def test_multiple_patterns_in_one_file(self):
        source = """\
from fastapi.security import OAuth2PasswordBearer, HTTPBearer, HTTPBasic

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")
bearer = HTTPBearer()
basic = HTTPBasic()

@app.get("/protected")
async def protected(user = Depends(get_current_user), token = Security(oauth2_scheme)):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        # OAuth2PasswordBearer, HTTPBearer, HTTPBasic, Depends, Security = 5
        assert len(guards) == 5

    def test_determinism(self):
        source = """\
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")
bearer = HTTPBearer()

@app.get("/me")
async def me(user = Depends(get_current_user)):
    pass
"""
        result1 = self.detector.detect(_ctx(source))
        result2 = self.detector.detect(_ctx(source))
        assert len(result1.nodes) == len(result2.nodes)
        for n1, n2 in zip(result1.nodes, result2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind
            assert n1.properties == n2.properties
            assert n1.location == n2.location

    def test_line_numbers_are_correct(self):
        source = "import os\n\nbearer = HTTPBearer()\n"
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].location.line_start == 3
