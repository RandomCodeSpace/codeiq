package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

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
    void noMatchOnNonAuthCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "passport.use(new JwtStrategy(opts));\njwt.verify(token, secret);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
