package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DjangoViewDetectorTest {

    private final DjangoViewDetector detector = new DjangoViewDetector();

    @Test
    void detectsUrlPattern() {
        String code = """
                urlpatterns = [
                    path('api/users/', UserView.as_view(), name='user-list'),
                ]
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("urls.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(0).getKind());
        assertEquals("api/users/", result.nodes().get(0).getLabel());
        assertEquals("django", result.nodes().get(0).getProperties().get("framework"));
    }

    @Test
    void detectsClassBasedView() {
        String code = """
                class UserView(APIView):
                    def get(self, request):
                        pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("views.py", "python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.CLASS, result.nodes().get(0).getKind());
        assertEquals("UserView", result.nodes().get(0).getLabel());
    }

    @Test
    void noMatchWithoutUrlpatterns() {
        String code = """
                path('api/users/', UserView.as_view())
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        // No urlpatterns keyword, so no endpoint detection
        assertEquals(0, result.nodes().size());
    }

    @Test
    void noMatchOnPlainClass() {
        String code = """
                class UserService(object):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                urlpatterns = [
                    path('api/users/', UserView.as_view()),
                ]

                class UserView(APIView):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("urls.py", "python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
