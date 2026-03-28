"""Tests for Kubernetes RBAC detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.config.kubernetes_rbac import KubernetesRBACDetector
from code_intelligence.models.graph import EdgeKind, NodeKind


def _ctx(parsed_data, file_path: str = "rbac.yml") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="yaml",
        content=b"",
        parsed_data=parsed_data,
        module_name="test-module",
    )


class TestKubernetesRBACDetector:
    def setup_method(self):
        self.detector = KubernetesRBACDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "config.kubernetes_rbac"
        assert self.detector.supported_languages == ("yaml",)

    def test_detect_role(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "Role",
                "metadata": {"name": "pod-reader", "namespace": "default"},
                "rules": [
                    {"apiGroups": [""], "resources": ["pods"], "verbs": ["get", "list"]},
                ],
            },
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        guard = guards[0]
        assert guard.id == "k8s_rbac:rbac.yml:Role:default/pod-reader"
        assert guard.label == "Role/pod-reader"
        assert guard.properties["auth_type"] == "k8s_rbac"
        assert guard.properties["k8s_kind"] == "Role"
        assert guard.properties["namespace"] == "default"
        assert len(guard.properties["rules"]) == 1
        assert guard.properties["rules"][0]["resources"] == ["pods"]
        assert guard.properties["rules"][0]["verbs"] == ["get", "list"]

    def test_detect_cluster_role(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "ClusterRole",
                "metadata": {"name": "cluster-admin"},
                "rules": [
                    {"apiGroups": ["*"], "resources": ["*"], "verbs": ["*"]},
                ],
            },
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        guard = guards[0]
        assert guard.id == "k8s_rbac:rbac.yml:ClusterRole:default/cluster-admin"
        assert guard.properties["k8s_kind"] == "ClusterRole"

    def test_detect_service_account(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "ServiceAccount",
                "metadata": {"name": "my-sa", "namespace": "production"},
            },
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        guard = guards[0]
        assert guard.id == "k8s_rbac:rbac.yml:ServiceAccount:production/my-sa"
        assert guard.label == "ServiceAccount/my-sa"
        assert guard.properties["auth_type"] == "k8s_rbac"
        assert guard.properties["k8s_kind"] == "ServiceAccount"
        assert guard.properties["rules"] == []

    def test_detect_role_binding(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "RoleBinding",
                "metadata": {"name": "read-pods", "namespace": "default"},
                "roleRef": {
                    "kind": "Role",
                    "name": "pod-reader",
                    "apiGroup": "rbac.authorization.k8s.io",
                },
                "subjects": [
                    {"kind": "ServiceAccount", "name": "my-sa", "namespace": "default"},
                ],
            },
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["k8s_kind"] == "RoleBinding"

    def test_protects_edge_role_to_service_account(self):
        """RoleBinding should create a PROTECTS edge from Role to ServiceAccount."""
        ctx = _ctx({
            "type": "yaml_multi",
            "documents": [
                {
                    "kind": "Role",
                    "metadata": {"name": "pod-reader", "namespace": "default"},
                    "rules": [
                        {"apiGroups": [""], "resources": ["pods"], "verbs": ["get", "list"]},
                    ],
                },
                {
                    "kind": "ServiceAccount",
                    "metadata": {"name": "my-sa", "namespace": "default"},
                },
                {
                    "kind": "RoleBinding",
                    "metadata": {"name": "read-pods", "namespace": "default"},
                    "roleRef": {
                        "kind": "Role",
                        "name": "pod-reader",
                        "apiGroup": "rbac.authorization.k8s.io",
                    },
                    "subjects": [
                        {"kind": "ServiceAccount", "name": "my-sa", "namespace": "default"},
                    ],
                },
            ],
        })
        result = self.detector.detect(ctx)

        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 3  # Role + ServiceAccount + RoleBinding

        protects_edges = [e for e in result.edges if e.kind == EdgeKind.PROTECTS]
        assert len(protects_edges) == 1
        edge = protects_edges[0]
        assert edge.source == "k8s_rbac:rbac.yml:Role:default/pod-reader"
        assert edge.target == "k8s_rbac:rbac.yml:ServiceAccount:default/my-sa"

    def test_protects_edge_cluster_role_binding(self):
        """ClusterRoleBinding should create PROTECTS edge from ClusterRole to SA."""
        ctx = _ctx({
            "type": "yaml_multi",
            "documents": [
                {
                    "kind": "ClusterRole",
                    "metadata": {"name": "admin-role"},
                    "rules": [
                        {"apiGroups": ["*"], "resources": ["*"], "verbs": ["*"]},
                    ],
                },
                {
                    "kind": "ServiceAccount",
                    "metadata": {"name": "admin-sa", "namespace": "kube-system"},
                },
                {
                    "kind": "ClusterRoleBinding",
                    "metadata": {"name": "admin-binding"},
                    "roleRef": {
                        "kind": "ClusterRole",
                        "name": "admin-role",
                        "apiGroup": "rbac.authorization.k8s.io",
                    },
                    "subjects": [
                        {"kind": "ServiceAccount", "name": "admin-sa", "namespace": "kube-system"},
                    ],
                },
            ],
        })
        result = self.detector.detect(ctx)

        protects_edges = [e for e in result.edges if e.kind == EdgeKind.PROTECTS]
        assert len(protects_edges) == 1
        edge = protects_edges[0]
        assert "ClusterRole" in edge.source
        assert "ServiceAccount" in edge.target

    def test_empty_parsed_data(self):
        ctx = _ctx(None)
        result = self.detector.detect(ctx)
        assert result.nodes == []
        assert result.edges == []

    def test_non_rbac_kind_ignored(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "Deployment",
                "metadata": {"name": "my-app", "namespace": "default"},
                "spec": {},
            },
        })
        result = self.detector.detect(ctx)
        assert result.nodes == []

    def test_yaml_multi_mixed_kinds(self):
        """Only RBAC kinds should be processed, others should be ignored."""
        ctx = _ctx({
            "type": "yaml_multi",
            "documents": [
                {
                    "kind": "Deployment",
                    "metadata": {"name": "my-app", "namespace": "default"},
                    "spec": {},
                },
                {
                    "kind": "Role",
                    "metadata": {"name": "pod-reader", "namespace": "default"},
                    "rules": [],
                },
                {
                    "kind": "Service",
                    "metadata": {"name": "my-svc", "namespace": "default"},
                    "spec": {},
                },
            ],
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["k8s_kind"] == "Role"

    def test_missing_metadata_defaults(self):
        """Missing namespace should default to 'default'."""
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "Role",
                "metadata": {"name": "my-role"},
                "rules": [],
            },
        })
        result = self.detector.detect(ctx)
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["namespace"] == "default"
        assert guards[0].id == "k8s_rbac:rbac.yml:Role:default/my-role"

    def test_no_protects_edge_without_matching_role(self):
        """RoleBinding referencing a role not in documents should produce no edges."""
        ctx = _ctx({
            "type": "yaml_multi",
            "documents": [
                {
                    "kind": "ServiceAccount",
                    "metadata": {"name": "my-sa", "namespace": "default"},
                },
                {
                    "kind": "RoleBinding",
                    "metadata": {"name": "binding", "namespace": "default"},
                    "roleRef": {
                        "kind": "Role",
                        "name": "nonexistent-role",
                        "apiGroup": "rbac.authorization.k8s.io",
                    },
                    "subjects": [
                        {"kind": "ServiceAccount", "name": "my-sa", "namespace": "default"},
                    ],
                },
            ],
        })
        result = self.detector.detect(ctx)
        assert len(result.edges) == 0

    def test_no_protects_edge_without_matching_sa(self):
        """RoleBinding referencing a SA not in documents should produce no edges."""
        ctx = _ctx({
            "type": "yaml_multi",
            "documents": [
                {
                    "kind": "Role",
                    "metadata": {"name": "pod-reader", "namespace": "default"},
                    "rules": [],
                },
                {
                    "kind": "RoleBinding",
                    "metadata": {"name": "binding", "namespace": "default"},
                    "roleRef": {
                        "kind": "Role",
                        "name": "pod-reader",
                        "apiGroup": "rbac.authorization.k8s.io",
                    },
                    "subjects": [
                        {"kind": "ServiceAccount", "name": "nonexistent-sa", "namespace": "default"},
                    ],
                },
            ],
        })
        result = self.detector.detect(ctx)
        assert len(result.edges) == 0

    def test_multiple_rules(self):
        ctx = _ctx({
            "type": "yaml",
            "data": {
                "kind": "Role",
                "metadata": {"name": "multi-rule", "namespace": "default"},
                "rules": [
                    {"apiGroups": [""], "resources": ["pods"], "verbs": ["get"]},
                    {"apiGroups": ["apps"], "resources": ["deployments"], "verbs": ["create", "delete"]},
                ],
            },
        })
        result = self.detector.detect(ctx)
        guard = result.nodes[0]
        assert len(guard.properties["rules"]) == 2
        assert guard.properties["rules"][1]["resources"] == ["deployments"]
        assert guard.properties["rules"][1]["verbs"] == ["create", "delete"]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(None))
        assert isinstance(result, DetectorResult)
