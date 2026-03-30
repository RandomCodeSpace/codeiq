package io.github.randomcodespace.iq.detector.auth;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SessionHeaderAuthDetectorTest {
    private final SessionHeaderAuthDetector detector = new SessionHeaderAuthDetector();

    @Test void detectsSession() {
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", "const session = require('express-session');");
        DetectorResult r = detector.detect(ctx);
        assertEquals(1, r.nodes().size());
        assertEquals(NodeKind.MIDDLEWARE, r.nodes().get(0).getKind());
    }

    @Test void noMatchOnPlainCode() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "public class Foo {}");
        DetectorResult r = detector.detect(ctx);
        assertEquals(0, r.nodes().size());
    }

    @Test void deterministic() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "HttpSession session;\n@SessionAttributes");
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
