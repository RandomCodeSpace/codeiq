"""Tests for Helm chart detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.config.helm_chart import HelmChartDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(
    content: str = "",
    parsed_data=None,
    file_path: str = "charts/myapp/Chart.yaml",
) -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="yaml",
        content=content.encode(),
        parsed_data=parsed_data,
        module_name="test-module",
    )


class TestHelmChartDetector:
    def setup_method(self):
        self.detector = HelmChartDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "helm_chart"
        assert self.detector.supported_languages == ("yaml",)

    # --- Positive: Chart.yaml detection ---

    def test_detects_chart_yaml_basic(self):
        ctx = _ctx(
            file_path="charts/myapp/Chart.yaml",
            parsed_data={
                "type": "yaml",
                "data": {
                    "apiVersion": "v2",
                    "name": "myapp",
                    "version": "1.2.3",
                },
            },
        )
        result = self.detector.detect(ctx)
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 1
        assert modules[0].properties["chart_name"] == "myapp"
        assert modules[0].properties["chart_version"] == "1.2.3"
        assert modules[0].label == "helm:myapp"

    def test_detects_chart_yaml_with_dependencies(self):
        ctx = _ctx(
            file_path="charts/myapp/Chart.yaml",
            parsed_data={
                "type": "yaml",
                "data": {
                    "name": "myapp",
                    "version": "1.0.0",
                    "dependencies": [
                        {
                            "name": "postgresql",
                            "version": "11.6.0",
                            "repository": "https://charts.bitnami.com/bitnami",
                        },
                        {
                            "name": "redis",
                            "version": "17.0.0",
                            "repository": "https://charts.bitnami.com/bitnami",
                        },
                    ],
                },
            },
        )
        result = self.detector.detect(ctx)
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 3  # chart + 2 deps
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 2
        dep_names = {e.label for e in dep_edges}
        assert "myapp depends on postgresql" in dep_names
        assert "myapp depends on redis" in dep_names

    def test_chart_yaml_dep_properties(self):
        ctx = _ctx(
            file_path="charts/myapp/Chart.yaml",
            parsed_data={
                "type": "yaml",
                "data": {
                    "name": "myapp",
                    "version": "1.0.0",
                    "dependencies": [
                        {
                            "name": "postgresql",
                            "version": "11.6.0",
                            "repository": "https://charts.bitnami.com/bitnami",
                        },
                    ],
                },
            },
        )
        result = self.detector.detect(ctx)
        dep_nodes = [n for n in result.nodes if n.properties.get("type") == "helm_dependency"]
        assert len(dep_nodes) == 1
        assert dep_nodes[0].properties["chart_version"] == "11.6.0"
        assert dep_nodes[0].properties["repository"] == "https://charts.bitnami.com/bitnami"

    # --- Positive: values.yaml detection ---

    def test_detects_values_yaml(self):
        ctx = _ctx(
            file_path="charts/myapp/values.yaml",
            parsed_data={
                "type": "yaml",
                "data": {
                    "replicaCount": 3,
                    "image": {"repository": "myapp", "tag": "latest"},
                    "service": {"type": "ClusterIP", "port": 80},
                },
            },
        )
        result = self.detector.detect(ctx)
        config_keys = [n for n in result.nodes if n.kind == NodeKind.CONFIG_KEY]
        assert len(config_keys) == 3
        key_names = {n.properties["key"] for n in config_keys}
        assert key_names == {"replicaCount", "image", "service"}
        for node in config_keys:
            assert node.properties["helm_value"] is True

    def test_values_yaml_requires_helm_path(self):
        """values.yaml outside charts/ or helm/ should be ignored."""
        ctx = _ctx(
            file_path="config/values.yaml",
            parsed_data={
                "type": "yaml",
                "data": {"key": "value"},
            },
        )
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0

    # --- Positive: Template detection ---

    def test_detects_template_values_references(self):
        source = """\
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .Values.appName }}
spec:
  replicas: {{ .Values.replicaCount }}
  template:
    spec:
      containers:
        - image: {{ .Values.image.repository }}:{{ .Values.image.tag }}
"""
        ctx = _ctx(
            content=source,
            file_path="charts/myapp/templates/deployment.yaml",
        )
        result = self.detector.detect(ctx)
        reads_edges = [e for e in result.edges if e.kind == EdgeKind.READS_CONFIG]
        keys = {e.properties["key"] for e in reads_edges}
        assert "appName" in keys
        assert "replicaCount" in keys
        assert "image.repository" in keys
        assert "image.tag" in keys

    def test_detects_template_include(self):
        source = """\
{{- include "myapp.fullname" . }}
---
metadata:
  labels:
    {{- include "myapp.labels" . | nindent 4 }}
"""
        ctx = _ctx(
            content=source,
            file_path="charts/myapp/templates/deployment.yaml",
        )
        result = self.detector.detect(ctx)
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        helpers = {e.properties["helper"] for e in import_edges}
        assert "myapp.fullname" in helpers
        assert "myapp.labels" in helpers

    def test_template_mixed_values_and_includes(self):
        source = """\
apiVersion: v1
kind: Service
metadata:
  name: {{ include "myapp.fullname" . }}
spec:
  ports:
    - port: {{ .Values.service.port }}
"""
        ctx = _ctx(
            content=source,
            file_path="charts/myapp/templates/service.yaml",
        )
        result = self.detector.detect(ctx)
        reads_edges = [e for e in result.edges if e.kind == EdgeKind.READS_CONFIG]
        assert len(reads_edges) == 1
        assert reads_edges[0].properties["key"] == "service.port"
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1
        assert import_edges[0].properties["helper"] == "myapp.fullname"

    # --- Negative tests ---

    def test_empty_parsed_data(self):
        ctx = _ctx(file_path="charts/myapp/Chart.yaml", parsed_data=None)
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_chart_yaml(self):
        ctx = _ctx(
            file_path="config/settings.yaml",
            parsed_data={
                "type": "yaml",
                "data": {"key": "value"},
            },
        )
        result = self.detector.detect(ctx)
        assert len(result.nodes) == 0

    def test_non_template_yaml(self):
        """YAML files outside templates/ directory should not trigger template detection."""
        source = "replicas: {{ .Values.replicaCount }}"
        ctx = _ctx(content=source, file_path="charts/myapp/values.yaml")
        # values.yaml without /charts/ or /helm/ prefix still doesn't match values detection
        # and it's not in templates/ so template detection doesn't trigger
        result = self.detector.detect(ctx)
        assert len(result.edges) == 0

    # --- Determinism tests ---

    def test_determinism_chart_yaml(self):
        ctx = _ctx(
            file_path="charts/myapp/Chart.yaml",
            parsed_data={
                "type": "yaml",
                "data": {
                    "name": "myapp",
                    "version": "1.0.0",
                    "dependencies": [
                        {"name": "redis", "version": "1.0.0", "repository": "https://example.com"},
                        {"name": "postgres", "version": "2.0.0", "repository": "https://example.com"},
                    ],
                },
            },
        )
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)

    def test_determinism_template(self):
        source = """\
{{ .Values.a }}
{{ .Values.b }}
{{ include "helper1" . }}
{{ include "helper2" . }}
"""
        ctx = _ctx(content=source, file_path="charts/myapp/templates/test.yaml")
        r1 = self.detector.detect(ctx)
        r2 = self.detector.detect(ctx)
        assert len(r1.edges) == len(r2.edges)
        assert [e.target for e in r1.edges] == [e.target for e in r2.edges]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(parsed_data=None, file_path="charts/myapp/Chart.yaml"))
        assert isinstance(result, DetectorResult)
