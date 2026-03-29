package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastAPIAuthDetectorTest {

    private final FastAPIAuthDetector detector = new FastAPIAuthDetector();

    @Test
    void detectsDependsAuth() {
        String code = """
                async def get_items(user=Depends(get_current_user)):
                    return []
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.GUARD, result.nodes().get(0).getKind());
        assertEquals("Depends(get_current_user)", result.nodes().get(0).getLabel());
        assertEquals("fastapi", result.nodes().get(0).getProperties().get("auth_type"));
    }

    @Test
    void detectsSecurityScheme() {
        String code = """
                async def protected(token=Security(oauth2_scheme)):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("Security(oauth2_scheme)", result.nodes().get(0).getLabel());
        assertEquals("oauth2_scheme", result.nodes().get(0).getProperties().get("scheme"));
    }

    @Test
    void detectsOAuth2PasswordBearer() {
        String code = """
                oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/token")
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("/auth/token", result.nodes().get(0).getProperties().get("token_url"));
    }

    @Test
    void detectsHTTPBearer() {
        String code = """
                bearer = HTTPBearer()
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("HTTPBearer()", result.nodes().get(0).getLabel());
        assertEquals("bearer", result.nodes().get(0).getProperties().get("auth_flow"));
    }

    @Test
    void detectsHTTPBasic() {
        String code = """
                basic = HTTPBasic()
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("HTTPBasic()", result.nodes().get(0).getLabel());
        assertEquals("basic", result.nodes().get(0).getProperties().get("auth_flow"));
    }

    @Test
    void noMatchOnPlainCode() {
        String code = """
                def hello():
                    return "world"
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                oauth2 = OAuth2PasswordBearer(tokenUrl="/token")
                bearer = HTTPBearer()

                async def protected(user=Depends(get_current_user)):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
