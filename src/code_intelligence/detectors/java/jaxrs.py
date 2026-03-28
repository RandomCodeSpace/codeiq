"""JAX-RS REST endpoint detector for Java source files."""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_PATH_RE = re.compile(r'@Path\s*\(\s*"([^"]*)"')
_HTTP_METHOD_RE = re.compile(r"@(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\b")
_PRODUCES_RE = re.compile(r'@Produces\s*\(\s*\{?\s*(?:MediaType\.\w+|"([^"]*)")')
_CONSUMES_RE = re.compile(r'@Consumes\s*\(\s*\{?\s*(?:MediaType\.\w+|"([^"]*)")')
_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")
_JAVA_METHOD_RE = re.compile(
    r'(?:public|protected|private)?\s*(?:static\s+)?(?:[\w<>\[\],\s]+)\s+(\w+)\s*\('
)


class JaxrsDetector:
    """Detects JAX-RS REST endpoints from annotations."""

    name: str = "jaxrs"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        # Fast bail
        if "@Path" not in text and "javax.ws.rs" not in text and "jakarta.ws.rs" not in text:
            return result

        lines = text.split("\n")

        # Find class name and class-level @Path
        class_name: str | None = None
        class_base_path = ""
        for i, line in enumerate(lines):
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                # Look backwards for class-level @Path
                for j in range(max(0, i - 5), i):
                    pm = _PATH_RE.search(lines[j])
                    if pm:
                        class_base_path = pm.group(1).rstrip("/")
                        break
                break

        if not class_name:
            return result

        class_node_id = f"{ctx.file_path}:{class_name}"

        # Scan for method-level HTTP annotations
        for i, line in enumerate(lines):
            m = _HTTP_METHOD_RE.search(line)
            if not m:
                continue

            http_method = m.group(1)

            # Check whether this annotation is at the class level by
            # inspecting the subsequent non-empty, non-annotation lines.
            is_class_level = False
            for k in range(i + 1, min(i + 5, len(lines))):
                stripped = lines[k].strip()
                if stripped.startswith("@") or not stripped:
                    continue
                if "class " in stripped or "interface " in stripped:
                    is_class_level = True
                break
            if is_class_level:
                continue

            # Look for a nearby method-level @Path (within a few lines before
            # and after the HTTP annotation)
            method_path: str | None = None
            for k in range(max(0, i - 3), min(i + 4, len(lines))):
                if k == i:
                    continue
                pm = _PATH_RE.search(lines[k])
                if pm:
                    method_path = pm.group(1)
                    break

            # Combine class base path with method path
            if method_path is not None:
                full_path = f"{class_base_path}/{method_path.lstrip('/')}"
            else:
                full_path = class_base_path or "/"
            if not full_path.startswith("/"):
                full_path = "/" + full_path

            # Extract @Produces and @Consumes near the annotation
            produces: str | None = None
            consumes: str | None = None
            for k in range(max(0, i - 5), min(i + 5, len(lines))):
                if produces is None:
                    pm = _PRODUCES_RE.search(lines[k])
                    if pm:
                        produces = pm.group(1)  # may be None for MediaType constant
                if consumes is None:
                    cm = _CONSUMES_RE.search(lines[k])
                    if cm:
                        consumes = cm.group(1)  # may be None for MediaType constant

            # Find the method name on subsequent lines
            method_name: str | None = None
            for k in range(i + 1, min(i + 5, len(lines))):
                mm = _JAVA_METHOD_RE.search(lines[k])
                if mm:
                    method_name = mm.group(1)
                    break

            endpoint_label = f"{http_method} {full_path}"
            endpoint_id = (
                f"{ctx.file_path}:{class_name}:{method_name or 'unknown'}"
                f":{http_method}:{full_path}"
            )

            properties: dict[str, Any] = {
                "http_method": http_method,
                "path": full_path,
            }
            if produces:
                properties["produces"] = produces
            if consumes:
                properties["consumes"] = consumes

            node = GraphNode(
                id=endpoint_id,
                kind=NodeKind.ENDPOINT,
                label=endpoint_label,
                fqn=f"{class_name}.{method_name}" if method_name else class_name,
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                annotations=[f"@{http_method}"],
                properties=properties,
            )
            result.nodes.append(node)

            edge = GraphEdge(
                source=class_node_id,
                target=endpoint_id,
                kind=EdgeKind.EXPOSES,
                label=f"{class_name} exposes {endpoint_label}",
            )
            result.edges.append(edge)

        return result
