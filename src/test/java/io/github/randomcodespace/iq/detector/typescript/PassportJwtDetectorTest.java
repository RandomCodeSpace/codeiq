package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PassportJwtDetectorTest {

    private final PassportJwtDetector detector = new PassportJwtDetector();

    @Test
    void detectsPassportAndJwt() {
        String code = """
                passport.use(new JwtStrategy(opts, verify));
                passport.authenticate('jwt');
                jwt.verify(token, secret);
                const expressJwt = require('express-jwt');
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(4, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.GUARD));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.MIDDLEWARE));
        assertEquals("passport", result.nodes().get(0).getProperties().get("auth_type"));
    }

    @Test
    void detectsPassportUseWithJwtStrategy() {
        String code = "passport.use(new JwtStrategy({ secretOrKey: 'secret' }, callback));";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var node = result.nodes().get(0);
        assertEquals(NodeKind.GUARD, node.getKind());
        assertEquals("passport", node.getProperties().get("auth_type"));
        assertEquals("JwtStrategy", node.getProperties().get("strategy"));
    }

    @Test
    void detectsPassportUseWithLocalStrategy() {
        String code = "passport.use(new LocalStrategy({ usernameField: 'email' }, cb));";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("LocalStrategy", result.nodes().get(0).getProperties().get("strategy"));
    }

    @Test
    void detectsPassportAuthenticate() {
        String code = "app.post('/login', passport.authenticate('local', { session: false }));";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/routes.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var node = result.nodes().get(0);
        assertEquals(NodeKind.MIDDLEWARE, node.getKind());
        assertEquals("jwt", node.getProperties().get("auth_type"));
        assertEquals("local", node.getProperties().get("strategy"));
    }

    @Test
    void detectsJwtVerify() {
        String code = "const decoded = jwt.verify(token, process.env.JWT_SECRET);";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/middleware.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(NodeKind.MIDDLEWARE, result.nodes().get(0).getKind());
        assertEquals("jwt", result.nodes().get(0).getProperties().get("auth_type"));
    }

    @Test
    void detectsRequireExpressJwt() {
        String code = "const expressJwt = require('express-jwt');";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(NodeKind.MIDDLEWARE, result.nodes().get(0).getKind());
        assertEquals("express-jwt", result.nodes().get(0).getProperties().get("library"));
    }

    @Test
    void detectsImportExpressJwt() {
        String code = "import { expressjwt } from 'express-jwt';";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(NodeKind.MIDDLEWARE, result.nodes().get(0).getKind());
        assertEquals("express-jwt", result.nodes().get(0).getProperties().get("library"));
    }

    @Test
    void detectsMultipleStrategies() {
        String code = """
                passport.use(new JwtStrategy(opts, cb));
                passport.use(new GoogleStrategy(opts, cb));
                passport.use(new GitHubStrategy(opts, cb));
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "JwtStrategy".equals(n.getProperties().get("strategy")));
        assertThat(result.nodes()).anyMatch(n -> "GoogleStrategy".equals(n.getProperties().get("strategy")));
        assertThat(result.nodes()).anyMatch(n -> "GitHubStrategy".equals(n.getProperties().get("strategy")));
    }

    @Test
    void noMatchOnNonAuthCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noEdgesReturned() {
        String code = "passport.use(new JwtStrategy(opts));\njwt.verify(token, secret);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "passport.use(new JwtStrategy(opts));\njwt.verify(token, secret);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.passport_jwt", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
