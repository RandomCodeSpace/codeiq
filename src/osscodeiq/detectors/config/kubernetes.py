"""Kubernetes manifest detector for container orchestration resource definitions."""

from __future__ import annotations

from typing import Any

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_K8S_KINDS: frozenset[str] = frozenset({
    "Deployment",
    "Service",
    "ConfigMap",
    "Secret",
    "Ingress",
    "Pod",
    "StatefulSet",
    "DaemonSet",
    "Job",
    "CronJob",
    "Namespace",
    "PersistentVolumeClaim",
    "ServiceAccount",
    "Role",
    "RoleBinding",
    "ClusterRole",
    "ClusterRoleBinding",
})


def _is_k8s_doc(doc: Any) -> bool:
    """Check whether a parsed YAML document looks like a Kubernetes resource."""
    return isinstance(doc, dict) and doc.get("kind") in _K8S_KINDS


def _get_documents(ctx: DetectorContext) -> list[dict[str, Any]]:
    """Extract Kubernetes documents from parsed data."""
    if not ctx.parsed_data:
        return []

    ptype = ctx.parsed_data.get("type")

    if ptype == "yaml_multi":
        docs = ctx.parsed_data.get("documents", [])
        if isinstance(docs, list):
            return [d for d in docs if _is_k8s_doc(d)]
        return []

    if ptype == "yaml":
        data = ctx.parsed_data.get("data")
        if _is_k8s_doc(data):
            return [data]
        return []

    return []


def _safe_str(val: Any) -> str:
    """Safely convert a value to string."""
    if val is None:
        return ""
    return str(val)


class KubernetesDetector:
    """Detects Kubernetes resources, container specs, and cross-resource relationships."""

    name: str = "kubernetes"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        documents = _get_documents(ctx)
        if not documents:
            return result

        fp = ctx.file_path

        # Track deployments by their match labels for service selector resolution
        deployment_labels: dict[str, str] = {}  # "label_key=label_val" -> node_id
        # Track services with selectors
        service_selectors: list[tuple[str, dict[str, str]]] = []  # (node_id, selector)
        # Track ingress backends
        ingress_backends: list[tuple[str, str]] = []  # (ingress_node_id, service_name)

        for doc in documents:
            kind = doc.get("kind", "")
            metadata = doc.get("metadata") or {}
            if not isinstance(metadata, dict):
                metadata = {}

            name = _safe_str(metadata.get("name", "unknown"))
            namespace = _safe_str(metadata.get("namespace", "default")) or "default"
            labels = metadata.get("labels")
            annotations = metadata.get("annotations")

            node_id = f"k8s:{fp}:{kind}:{namespace}/{name}"

            props: dict[str, Any] = {"kind": kind, "namespace": namespace}
            if isinstance(labels, dict):
                props["labels"] = labels
            if isinstance(annotations, dict):
                props["annotations"] = annotations

            # INFRA_RESOURCE node for the resource
            result.nodes.append(GraphNode(
                id=node_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=f"{kind}/{name}",
                fqn=f"k8s:{kind}:{namespace}/{name}",
                module=ctx.module_name,
                location=SourceLocation(file_path=fp),
                properties=props,
            ))

            spec = doc.get("spec") or {}
            if not isinstance(spec, dict):
                spec = {}

            # Extract container specs from workload resources
            if kind in ("Deployment", "StatefulSet", "DaemonSet", "Job", "CronJob", "Pod"):
                containers = self._extract_containers(spec, kind)
                for container in containers:
                    c_name = _safe_str(container.get("name", "unnamed"))
                    c_props: dict[str, Any] = {}

                    image = container.get("image")
                    if image:
                        c_props["image"] = str(image)

                    # Ports
                    c_ports = container.get("ports")
                    if isinstance(c_ports, list):
                        port_strs = []
                        for p in c_ports:
                            if isinstance(p, dict):
                                port_strs.append(
                                    f"{p.get('containerPort', '?')}/{p.get('protocol', 'TCP')}"
                                )
                        if port_strs:
                            c_props["ports"] = port_strs

                    # Environment variables
                    env_vars = container.get("env")
                    if isinstance(env_vars, list):
                        env_names = []
                        for e in env_vars:
                            if isinstance(e, dict) and "name" in e:
                                env_names.append(str(e["name"]))
                        if env_names:
                            c_props["env_vars"] = env_names

                    result.nodes.append(GraphNode(
                        id=f"{node_id}:container:{c_name}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"{name}/{c_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties=c_props,
                    ))

            # Track deployment match labels for service selector linking
            if kind in ("Deployment", "StatefulSet", "DaemonSet"):
                template = spec.get("template") or {}
                if isinstance(template, dict):
                    tmpl_meta = template.get("metadata") or {}
                    if isinstance(tmpl_meta, dict):
                        tmpl_labels = tmpl_meta.get("labels")
                        if isinstance(tmpl_labels, dict):
                            for lk, lv in tmpl_labels.items():
                                deployment_labels[f"{lk}={lv}"] = node_id

                # Also use spec.selector.matchLabels
                selector = spec.get("selector") or {}
                if isinstance(selector, dict):
                    match_labels = selector.get("matchLabels")
                    if isinstance(match_labels, dict):
                        for lk, lv in match_labels.items():
                            deployment_labels[f"{lk}={lv}"] = node_id

            # Track service selectors
            if kind == "Service":
                svc_selector = spec.get("selector")
                if isinstance(svc_selector, dict):
                    service_selectors.append((node_id, svc_selector))

            # Track ingress backends
            if kind == "Ingress":
                self._collect_ingress_backends(spec, node_id, ingress_backends)

        # Resolve service selector -> deployment edges
        for svc_node_id, selector in service_selectors:
            for sel_key, sel_val in selector.items():
                label_tag = f"{sel_key}={sel_val}"
                if label_tag in deployment_labels:
                    result.edges.append(GraphEdge(
                        source=svc_node_id,
                        target=deployment_labels[label_tag],
                        kind=EdgeKind.DEPENDS_ON,
                        label=f"service selects {label_tag}",
                        properties={"selector": label_tag},
                    ))

        # Resolve ingress -> service edges
        # Build a lookup of service names to their node IDs
        service_name_to_id: dict[str, str] = {}
        for doc in documents:
            if doc.get("kind") != "Service":
                continue
            meta = doc.get("metadata") or {}
            if isinstance(meta, dict):
                svc_name = _safe_str(meta.get("name", ""))
                ns = _safe_str(meta.get("namespace", "default")) or "default"
                svc_nid = f"k8s:{fp}:Service:{ns}/{svc_name}"
                service_name_to_id[svc_name] = svc_nid

        for ingress_nid, backend_svc in ingress_backends:
            target_id = service_name_to_id.get(backend_svc)
            if target_id:
                result.edges.append(GraphEdge(
                    source=ingress_nid,
                    target=target_id,
                    kind=EdgeKind.CONNECTS_TO,
                    label=f"ingress routes to {backend_svc}",
                ))

        return result

    @staticmethod
    def _extract_containers(spec: dict[str, Any], kind: str) -> list[dict[str, Any]]:
        """Extract container definitions from a workload spec, navigating nested templates."""
        containers: list[dict[str, Any]] = []

        if kind == "Pod":
            cs = spec.get("containers")
            if isinstance(cs, list):
                containers.extend(c for c in cs if isinstance(c, dict))
            return containers

        if kind == "CronJob":
            job_template = spec.get("jobTemplate") or {}
            if isinstance(job_template, dict):
                spec = job_template.get("spec") or {}
                if not isinstance(spec, dict):
                    return containers

        template = spec.get("template") or {}
        if isinstance(template, dict):
            pod_spec = template.get("spec") or {}
            if isinstance(pod_spec, dict):
                cs = pod_spec.get("containers")
                if isinstance(cs, list):
                    containers.extend(c for c in cs if isinstance(c, dict))
                init_cs = pod_spec.get("initContainers")
                if isinstance(init_cs, list):
                    containers.extend(c for c in init_cs if isinstance(c, dict))

        return containers

    @staticmethod
    def _collect_ingress_backends(
        spec: dict[str, Any],
        ingress_node_id: str,
        out: list[tuple[str, str]],
    ) -> None:
        """Collect backend service references from an Ingress spec."""
        # Default backend
        default_backend = spec.get("defaultBackend") or spec.get("backend")
        if isinstance(default_backend, dict):
            svc = default_backend.get("service") or default_backend
            if isinstance(svc, dict):
                svc_name = svc.get("name") or svc.get("serviceName")
                if svc_name:
                    out.append((ingress_node_id, str(svc_name)))

        # Rules
        rules = spec.get("rules")
        if isinstance(rules, list):
            for rule in rules:
                if not isinstance(rule, dict):
                    continue
                http = rule.get("http") or {}
                if not isinstance(http, dict):
                    continue
                paths = http.get("paths")
                if not isinstance(paths, list):
                    continue
                for path_entry in paths:
                    if not isinstance(path_entry, dict):
                        continue
                    backend = path_entry.get("backend")
                    if not isinstance(backend, dict):
                        continue
                    svc = backend.get("service") or backend
                    if isinstance(svc, dict):
                        svc_name = svc.get("name") or svc.get("serviceName")
                        if svc_name:
                            out.append((ingress_node_id, str(svc_name)))
