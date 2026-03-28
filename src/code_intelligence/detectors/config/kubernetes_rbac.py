"""Kubernetes RBAC (Role-Based Access Control) detector."""

from __future__ import annotations

from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_RBAC_KINDS: frozenset[str] = frozenset({
    "Role",
    "ClusterRole",
    "RoleBinding",
    "ClusterRoleBinding",
    "ServiceAccount",
})


def _safe_str(val: Any) -> str:
    """Safely convert a value to string."""
    if val is None:
        return ""
    return str(val)


def _get_documents(ctx: DetectorContext) -> list[dict[str, Any]]:
    """Extract RBAC-related Kubernetes documents from parsed data."""
    if not ctx.parsed_data:
        return []

    ptype = ctx.parsed_data.get("type")

    if ptype == "yaml_multi":
        docs = ctx.parsed_data.get("documents", [])
        if isinstance(docs, list):
            return [
                d for d in docs
                if isinstance(d, dict) and d.get("kind") in _RBAC_KINDS
            ]
        return []

    if ptype == "yaml":
        data = ctx.parsed_data.get("data")
        if isinstance(data, dict) and data.get("kind") in _RBAC_KINDS:
            return [data]
        return []

    return []


def _make_node_id(file_path: str, kind: str, namespace: str, name: str) -> str:
    """Build deterministic node ID for a K8s RBAC resource."""
    return f"k8s_rbac:{file_path}:{kind}:{namespace}/{name}"


class KubernetesRBACDetector:
    """Detects Kubernetes RBAC resources and produces GUARD nodes and PROTECTS edges."""

    name: str = "config.kubernetes_rbac"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        documents = _get_documents(ctx)
        if not documents:
            return result

        fp = ctx.file_path

        # Collect nodes first so we can resolve bindings
        role_nodes: dict[str, str] = {}       # "kind:namespace/name" -> node_id
        sa_nodes: dict[str, str] = {}          # "namespace/name" -> node_id
        bindings: list[dict[str, Any]] = []

        for doc in documents:
            kind = doc.get("kind", "")
            metadata = doc.get("metadata") or {}
            if not isinstance(metadata, dict):
                metadata = {}

            name = _safe_str(metadata.get("name", "unknown"))
            namespace = _safe_str(metadata.get("namespace", "default")) or "default"

            node_id = _make_node_id(fp, kind, namespace, name)

            if kind in ("Role", "ClusterRole"):
                rules = doc.get("rules")
                if not isinstance(rules, list):
                    rules = []
                serialized_rules = []
                for rule in rules:
                    if isinstance(rule, dict):
                        serialized_rules.append({
                            "apiGroups": rule.get("apiGroups", []),
                            "resources": rule.get("resources", []),
                            "verbs": rule.get("verbs", []),
                        })

                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.GUARD,
                    label=f"{kind}/{name}",
                    fqn=f"k8s:{kind}:{namespace}/{name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={
                        "auth_type": "k8s_rbac",
                        "k8s_kind": kind,
                        "namespace": namespace,
                        "rules": serialized_rules,
                    },
                ))
                role_key = f"{kind}:{namespace}/{name}"
                # ClusterRoles are cluster-scoped; store with "cluster-wide" marker
                if kind == "ClusterRole":
                    role_key = f"ClusterRole:cluster-wide/{name}"
                role_nodes[role_key] = node_id

            elif kind == "ServiceAccount":
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.GUARD,
                    label=f"ServiceAccount/{name}",
                    fqn=f"k8s:ServiceAccount:{namespace}/{name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={
                        "auth_type": "k8s_rbac",
                        "k8s_kind": "ServiceAccount",
                        "namespace": namespace,
                        "rules": [],
                    },
                ))
                sa_nodes[f"{namespace}/{name}"] = node_id

            elif kind in ("RoleBinding", "ClusterRoleBinding"):
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.GUARD,
                    label=f"{kind}/{name}",
                    fqn=f"k8s:{kind}:{namespace}/{name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={
                        "auth_type": "k8s_rbac",
                        "k8s_kind": kind,
                        "namespace": namespace,
                        "rules": [],
                    },
                ))
                bindings.append(doc)

        # Resolve RoleBinding/ClusterRoleBinding -> PROTECTS edges
        for doc in bindings:
            kind = doc.get("kind", "")
            metadata = doc.get("metadata") or {}
            if not isinstance(metadata, dict):
                metadata = {}
            binding_namespace = _safe_str(metadata.get("namespace", "default")) or "default"

            role_ref = doc.get("roleRef")
            if not isinstance(role_ref, dict):
                continue

            ref_kind = _safe_str(role_ref.get("kind", ""))
            ref_name = _safe_str(role_ref.get("name", ""))

            # Resolve the role node
            if ref_kind == "ClusterRole":
                role_key = f"ClusterRole:cluster-wide/{ref_name}"
            else:
                role_key = f"{ref_kind}:{binding_namespace}/{ref_name}"

            role_nid = role_nodes.get(role_key)
            if not role_nid:
                continue

            subjects = doc.get("subjects")
            if not isinstance(subjects, list):
                continue

            for subject in subjects:
                if not isinstance(subject, dict):
                    continue
                subj_kind = _safe_str(subject.get("kind", ""))
                subj_name = _safe_str(subject.get("name", ""))
                subj_namespace = _safe_str(
                    subject.get("namespace", binding_namespace)
                ) or binding_namespace

                if subj_kind == "ServiceAccount":
                    sa_key = f"{subj_namespace}/{subj_name}"
                    sa_nid = sa_nodes.get(sa_key)
                    if sa_nid:
                        result.edges.append(GraphEdge(
                            source=role_nid,
                            target=sa_nid,
                            kind=EdgeKind.PROTECTS,
                            label=f"{ref_kind}/{ref_name} -> ServiceAccount/{subj_name}",
                            properties={
                                "binding_kind": kind,
                            },
                        ))

        return result
