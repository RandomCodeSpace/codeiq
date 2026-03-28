"""Tests for Passport.js / JWT detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.passport_jwt import PassportJwtDetector
from osscodeiq.models.graph import NodeKind


def _ctx(
    content: str,
    file_path: str = "auth.ts",
    language: str = "typescript",
) -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestPassportJwtDetector:
    def setup_method(self):
        self.detector = PassportJwtDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "typescript.passport_jwt"
        assert self.detector.supported_languages == ("typescript", "javascript")

    def test_detect_passport_use_jwt_strategy(self):
        source = """\
passport.use(new JwtStrategy(opts, (jwt_payload, done) => {
  User.findById(jwt_payload.sub).then(user => done(null, user));
}));
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        guard = guards[0]
        assert guard.properties["auth_type"] == "passport"
        assert guard.properties["strategy"] == "JwtStrategy"
        assert guard.id == "auth:auth.ts:passport.use(JwtStrategy):1"
        assert guard.label == "passport.use(JwtStrategy)"

    def test_detect_passport_use_local_strategy(self):
        source = """\
passport.use(new LocalStrategy((username, password, done) => {
  User.findOne({ username }).then(user => done(null, user));
}));
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["strategy"] == "LocalStrategy"

    def test_detect_passport_authenticate(self):
        source = """\
app.get('/protected', passport.authenticate('jwt', { session: false }), handler);
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        mw = middleware[0]
        assert mw.properties["auth_type"] == "jwt"
        assert mw.properties["strategy"] == "jwt"
        assert mw.id == "auth:auth.ts:passport.authenticate(jwt):1"
        assert mw.label == "passport.authenticate('jwt')"

    def test_detect_passport_authenticate_local(self):
        source = """\
app.post('/login', passport.authenticate('local'), (req, res) => {
  res.json({ token: generateToken(req.user) });
});
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        assert middleware[0].properties["strategy"] == "local"

    def test_detect_jwt_verify(self):
        source = """\
const decoded = jwt.verify(token, process.env.JWT_SECRET);
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        mw = middleware[0]
        assert mw.properties["auth_type"] == "jwt"
        assert mw.id == "auth:auth.ts:jwt.verify:1"
        assert mw.label == "jwt.verify()"

    def test_detect_require_express_jwt(self):
        source = """\
const expressJwt = require('express-jwt');
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        mw = middleware[0]
        assert mw.properties["auth_type"] == "jwt"
        assert mw.properties["library"] == "express-jwt"
        assert mw.id == "auth:auth.ts:require(express-jwt):1"

    def test_detect_import_expressjwt(self):
        source = """\
import { expressjwt } from 'express-jwt';
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1
        mw = middleware[0]
        assert mw.properties["auth_type"] == "jwt"
        assert mw.properties["library"] == "express-jwt"
        assert mw.id == "auth:auth.ts:import(expressjwt):1"

    def test_detect_import_expressjwt_with_other_imports(self):
        source = """\
import { expressjwt, ExpressJwtRequest } from 'express-jwt';
"""
        result = self.detector.detect(_ctx(source))
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middleware) == 1

    def test_empty_file(self):
        result = self.detector.detect(_ctx(""))
        assert result.nodes == []
        assert result.edges == []

    def test_no_auth_patterns(self):
        source = """\
app.get('/hello', (req, res) => {
  res.json({ message: 'Hello World' });
});
"""
        result = self.detector.detect(_ctx(source))
        assert result.nodes == []

    def test_javascript_language(self):
        source = """\
passport.use(new JwtStrategy(opts, callback));
"""
        result = self.detector.detect(_ctx(source, language="javascript", file_path="auth.js"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1

    def test_combined_passport_and_jwt(self):
        source = """\
const jwt = require('jsonwebtoken');
const expressJwt = require('express-jwt');

passport.use(new JwtStrategy(opts, verify));

app.get('/api', passport.authenticate('jwt', { session: false }), handler);

function verifyToken(token) {
  return jwt.verify(token, secret);
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        middleware = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        # 1 passport.use(JwtStrategy) -> GUARD
        assert len(guards) == 1
        # 1 require('express-jwt') + 1 passport.authenticate + 1 jwt.verify -> MIDDLEWARE
        assert len(middleware) == 3

    def test_line_numbers_are_correct(self):
        source = """\
// line 1
// line 2
passport.use(new JwtStrategy(opts, cb));
// line 4
passport.authenticate('jwt');
"""
        result = self.detector.detect(_ctx(source))
        all_nodes = result.nodes
        assert len(all_nodes) == 2
        lines = sorted(n.location.line_start for n in all_nodes)
        assert lines == [3, 5]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
