package io.github.randomcodespace.iq.detector.auth;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LdapAuthDetectorTest {
    private final LdapAuthDetector detector = new LdapAuthDetector();

    @Test void detectsJavaLdap() {
        DetectorContext ctx = DetectorTestUtils.contextFor("java", "LdapContextSource source = new LdapContextSource();");
        DetectorResult r = detector.detect(ctx);
        assertEquals(1, r.nodes().size());
        assertEquals(NodeKind.GUARD, r.nodes().get(0).getKind());
    }

    @Test void noMatchOnUnsupportedLanguage() {
        DetectorContext ctx = DetectorTestUtils.contextFor("go", "LdapContextSource source;");
        DetectorResult r = detector.detect(ctx);
        assertEquals(0, r.nodes().size());
    }

    @Test void deterministic() {
        DetectorContext ctx = DetectorTestUtils.contextFor("python", "AUTH_LDAP_SERVER_URI = 'ldap://server'\nAUTH_LDAP_BIND_DN = 'cn=admin'");
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
