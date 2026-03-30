package io.github.randomcodespace.iq.detector.proto;
import io.github.randomcodespace.iq.detector.*; import io.github.randomcodespace.iq.model.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class ProtoStructureDetectorTest {
    private final ProtoStructureDetector d = new ProtoStructureDetector();
    @Test void detectsServiceAndMessage() {
        DetectorResult r = d.detect(DetectorTestUtils.contextFor("proto", "package grpc.test;\nservice UserService {\n  rpc GetUser(GetUserReq) returns (User);\n}\nmessage User {\n  string name = 1;\n}"));
        assertTrue(r.nodes().size() >= 3);
    }
    @Test void noMatch() { assertEquals(0, d.detect(DetectorTestUtils.contextFor("proto", "// comment")).nodes().size()); }
    @Test void deterministic() { DetectorTestUtils.assertDeterministic(d, DetectorTestUtils.contextFor("proto", "service Svc {\n  rpc Do(Req) returns (Resp);\n}\nmessage Req {}")); }
}
