package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DjangoAuthDetectorTest {

    private final DjangoAuthDetector detector = new DjangoAuthDetector();

    @Test
    void detectsLoginRequired() {
        String code = """
                @login_required
                def my_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.GUARD, result.nodes().get(0).getKind());
        assertEquals("@login_required", result.nodes().get(0).getLabel());
    }

    @Test
    void detectsPermissionRequired() {
        String code = """
                @permission_required("app.can_edit")
                def edit_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("@permission_required(app.can_edit)", result.nodes().get(0).getLabel());
        assertEquals(List.of("app.can_edit"), result.nodes().get(0).getProperties().get("permissions"));
    }

    @Test
    void detectsUserPassesTest() {
        String code = """
                @user_passes_test(is_staff_check)
                def admin_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("is_staff_check", result.nodes().get(0).getProperties().get("test_function"));
    }

    @Test
    void detectsAuthMixin() {
        String code = """
                class MyView(LoginRequiredMixin, View):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("MyView(LoginRequiredMixin)", result.nodes().get(0).getLabel());
        assertEquals("LoginRequiredMixin", result.nodes().get(0).getProperties().get("mixin"));
    }

    @Test
    void noMatchOnPlainView() {
        String code = """
                class MyView(View):
                    def get(self, request):
                        pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                @login_required
                def view1(request):
                    pass

                @permission_required("app.edit")
                def view2(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
