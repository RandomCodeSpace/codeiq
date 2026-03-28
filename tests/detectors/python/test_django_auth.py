"""Tests for Django auth detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.django_auth import DjangoAuthDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, file_path: str = "views.py") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="python",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestDjangoAuthDetector:
    def setup_method(self):
        self.detector = DjangoAuthDetector()

    def test_supported_languages(self):
        assert self.detector.supported_languages == ("python",)
        assert self.detector.name == "django_auth"

    def test_empty_input(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
        assert result.nodes == []
        assert result.edges == []

    def test_no_match(self):
        source = """\
from django.http import JsonResponse

def index(request):
    return JsonResponse({"status": "ok"})
"""
        result = self.detector.detect(_ctx(source))
        assert result.nodes == []

    def test_login_required(self):
        source = """\
from django.contrib.auth.decorators import login_required

@login_required
def profile(request):
    return render(request, "profile.html")
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "django"
        assert node.properties["permissions"] == []
        assert node.properties["auth_required"] is True
        assert node.id == "auth:views.py:login_required:3"
        assert "@login_required" in node.annotations

    def test_permission_required(self):
        source = """\
@permission_required("blog.can_publish")
def publish_post(request, post_id):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "django"
        assert node.properties["permissions"] == ["blog.can_publish"]
        assert node.properties["auth_required"] is True
        assert "permission_required" in node.id

    def test_permission_required_single_quotes(self):
        source = """\
@permission_required('app.edit_item')
def edit_item(request):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["permissions"] == ["app.edit_item"]

    def test_user_passes_test(self):
        source = """\
@user_passes_test(lambda u: u.is_staff)
def staff_dashboard(request):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "django"
        assert node.properties["auth_required"] is True
        assert "user_passes_test" in node.id

    def test_user_passes_test_named_function(self):
        source = """\
def is_manager(user):
    return user.groups.filter(name="managers").exists()

@user_passes_test(is_manager)
def manager_view(request):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["test_function"] == "is_manager"

    def test_login_required_mixin(self):
        source = """\
class MyView(LoginRequiredMixin, TemplateView):
    template_name = "my_template.html"
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        node = guards[0]
        assert node.properties["auth_type"] == "django"
        assert node.properties["mixin"] == "LoginRequiredMixin"
        assert node.properties["class_name"] == "MyView"
        assert node.properties["auth_required"] is True

    def test_permission_required_mixin(self):
        source = """\
class EditPostView(PermissionRequiredMixin, UpdateView):
    permission_required = "blog.change_post"
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["mixin"] == "PermissionRequiredMixin"
        assert guards[0].properties["class_name"] == "EditPostView"

    def test_user_passes_test_mixin(self):
        source = """\
class StaffOnlyView(UserPassesTestMixin, DetailView):
    def test_func(self):
        return self.request.user.is_staff
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["mixin"] == "UserPassesTestMixin"

    def test_multiple_patterns_in_one_file(self):
        source = """\
from django.contrib.auth.decorators import login_required, permission_required

@login_required
def dashboard(request):
    pass

@permission_required("app.can_edit")
def edit(request):
    pass

@user_passes_test(lambda u: u.is_superuser)
def admin_panel(request):
    pass

class ProtectedView(LoginRequiredMixin, TemplateView):
    pass
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        # login_required, permission_required, user_passes_test, LoginRequiredMixin = 4
        assert len(guards) == 4

    def test_determinism(self):
        source = """\
@login_required
def view1(request):
    pass

@permission_required("app.perm")
def view2(request):
    pass
"""
        result1 = self.detector.detect(_ctx(source))
        result2 = self.detector.detect(_ctx(source))
        assert len(result1.nodes) == len(result2.nodes)
        for n1, n2 in zip(result1.nodes, result2.nodes):
            assert n1.id == n2.id
            assert n1.kind == n2.kind
            assert n1.properties == n2.properties
            assert n1.location == n2.location

    def test_line_numbers_are_correct(self):
        source = "import os\n\n@login_required\ndef view(request):\n    pass\n"
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].location.line_start == 3
