"""Tests for Django view detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.django_views import DjangoViewDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, path: str = "urls.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestDjangoViewDetector:
    def setup_method(self):
        self.detector = DjangoViewDetector()

    def test_detects_urlpatterns(self):
        source = """\
from django.urls import path
from .views import UserListView, UserDetailView

urlpatterns = [
    path('api/users/', UserListView.as_view(), name='user-list'),
    path('api/users/<int:pk>/', UserDetailView.as_view(), name='user-detail'),
]
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        paths = {n.properties["path_pattern"] for n in endpoints}
        assert "api/users/" in paths
        assert "api/users/<int:pk>/" in paths

    def test_detects_class_based_views(self):
        source = """\
from rest_framework.views import APIView

class UserListView(APIView):
    def get(self, request):
        return Response(users)

    def post(self, request):
        return Response(status=201)
"""
        result = self.detector.detect(_ctx(source, path="views.py"))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert classes[0].label == "UserListView"
        assert classes[0].properties["framework"] == "django"

    def test_detects_viewset(self):
        source = """\
from rest_framework.viewsets import ModelViewSet

class OrderViewSet(ModelViewSet):
    queryset = Order.objects.all()
    serializer_class = OrderSerializer
"""
        result = self.detector.detect(_ctx(source, path="views.py"))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert "OrderViewSet" in classes[0].label

    def test_detects_re_path(self):
        source = """\
from django.urls import re_path

urlpatterns = [
    re_path('^api/search/$', search_view),
]
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0

    def test_no_urlpatterns(self):
        source = """\
def helper():
    return "not a view"
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
urlpatterns = [
    path('api/orders/', OrderListView.as_view()),
    path('api/orders/<pk>/', OrderDetailView.as_view()),
]

class OrderListView(APIView):
    pass
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
