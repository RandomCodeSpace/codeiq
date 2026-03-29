package io.github.randomcodespace.iq.detector.auth;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CertificateAuthDetectorTest {
    private final CertificateAuthDetector detector = new CertificateAuthDetector();

    @Test void detectsMtls() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "ssl_verify_client on;");
        DetectorResult r = detector.detect(ctx);
        assertEquals(1, r.nodes().size());
        assertEquals(NodeKind.GUARD, r.nodes().get(0).getKind());
        assertEquals("mtls", r.nodes().get(0).getProperties().get("auth_type"));
    }

    @Test void noMatchOnPlainCode() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "public class Foo {}");
        DetectorResult r = detector.detect(ctx);
        assertEquals(0, r.nodes().size());
    }

    @Test void deterministic() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "ssl_verify_client on;\nX509AuthenticationFilter filter;");
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
