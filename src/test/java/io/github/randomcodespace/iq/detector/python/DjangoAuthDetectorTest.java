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

    @Test
    void detectsPermissionRequiredMixin() {
        String code = """
                class AdminView(PermissionRequiredMixin, View):
                    permission_required = 'app.can_admin'
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("PermissionRequiredMixin", result.nodes().get(0).getProperties().get("mixin"));
        assertEquals("AdminView", result.nodes().get(0).getProperties().get("class_name"));
    }

    @Test
    void detectsUserPassesTestMixin() {
        String code = """
                class StaffView(UserPassesTestMixin, View):
                    def test_func(self):
                        return self.request.user.is_staff
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("UserPassesTestMixin", result.nodes().get(0).getProperties().get("mixin"));
    }

    @Test
    void loginRequiredHasAuthType() {
        String code = """
                @login_required
                def secure_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals("django", result.nodes().get(0).getProperties().get("auth_type"));
        assertEquals(true, result.nodes().get(0).getProperties().get("auth_required"));
    }

    @Test
    void loginRequiredHasAnnotations() {
        String code = """
                @login_required
                def secured(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        var node = result.nodes().get(0);
        assertTrue(node.getAnnotations().contains("@login_required"));
    }

    @Test
    void permissionRequiredHasPermissionsProperty() {
        String code = """
                @permission_required("myapp.view_report")
                def report_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        @SuppressWarnings("unchecked")
        List<String> perms = (List<String>) result.nodes().get(0).getProperties().get("permissions");
        assertNotNull(perms);
        assertFalse(perms.isEmpty());
        assertEquals("myapp.view_report", perms.get(0));
    }

    @Test
    void userPassesTestHasTestFunctionProperty() {
        String code = """
                @user_passes_test(lambda u: u.is_active)
                def restricted_view(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        // test_function should be set from arg
        assertNotNull(result.nodes().get(0).getProperties().get("test_function"));
    }

    @Test
    void mixinAnnotationFormat() {
        String code = """
                class SecureList(LoginRequiredMixin, ListView):
                    model = Item
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        var node = result.nodes().get(0);
        assertTrue(node.getAnnotations().stream().anyMatch(a -> a.contains("LoginRequiredMixin")));
    }

    @Test
    void noMatchOnEmptyContent() {
        DetectorContext ctx = DetectorTestUtils.contextFor("python", "");
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void multipleDecoratorsCapturedSeparately() {
        String code = """
                @login_required
                def view_a(request):
                    pass

                @login_required
                def view_b(request):
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
    }

    @Test
    void allAuthTypeIsDjango() {
        String code = """
                @login_required
                def v1(request): pass

                @permission_required("x.y")
                def v2(request): pass

                @user_passes_test(lambda u: True)
                def v3(request): pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertTrue(result.nodes().stream()
                .allMatch(n -> "django".equals(n.getProperties().get("auth_type"))));
    }
}
