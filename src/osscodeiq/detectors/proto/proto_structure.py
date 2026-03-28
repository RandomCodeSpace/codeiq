"""Protocol Buffers structure detector for services, RPCs, and messages."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_SERVICE_RE = re.compile(r'service\s+(\w+)\s*\{')
_RPC_RE = re.compile(r'rpc\s+(\w+)\s*\((\w+)\)\s*returns\s*\((\w+)\)')
_MESSAGE_RE = re.compile(r'message\s+(\w+)\s*\{')
_IMPORT_RE = re.compile(r'import\s+"([^"]+)"')
_PACKAGE_RE = re.compile(r'package\s+([\w.]+)\s*;')


class ProtoStructureDetector:
    """Detects Protobuf services, RPCs, messages, imports, and package declarations."""

    name: str = "proto_structure"
    supported_languages: tuple[str, ...] = ("proto",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        try:
            text = decode_text(ctx)
        except Exception:
            return result

        filepath = ctx.file_path
        lines = text.split("\n")

        # Package declaration
        for i, line in enumerate(lines):
            m = _PACKAGE_RE.search(line)
            if m:
                pkg_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"proto:{filepath}:package:{pkg_name}",
                    kind=NodeKind.CONFIG_KEY,
                    label=f"package {pkg_name}",
                    fqn=pkg_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=i + 1,
                    ),
                    properties={"package": pkg_name},
                ))
                break

        # Imports
        for i, line in enumerate(lines):
            m = _IMPORT_RE.search(line)
            if m:
                import_path = m.group(1)
                result.edges.append(GraphEdge(
                    source=filepath,
                    target=import_path,
                    kind=EdgeKind.IMPORTS,
                    label=f"{filepath} imports {import_path}",
                ))

        # Services and RPCs — track current service for RPC scoping
        current_service: str | None = None
        brace_depth = 0

        for i, line in enumerate(lines):
            # Track service blocks
            svc_match = _SERVICE_RE.search(line)
            if svc_match:
                svc_name = svc_match.group(1)
                current_service = svc_name
                brace_depth = 0

                result.nodes.append(GraphNode(
                    id=f"proto:{filepath}:service:{svc_name}",
                    kind=NodeKind.INTERFACE,
                    label=svc_name,
                    fqn=svc_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=i + 1,
                    ),
                ))

            # Track braces for service scope
            if current_service is not None:
                brace_depth += line.count("{") - line.count("}")
                if brace_depth <= 0:
                    current_service = None

            # RPCs
            rpc_match = _RPC_RE.search(line)
            if rpc_match:
                method_name = rpc_match.group(1)
                request_type = rpc_match.group(2)
                response_type = rpc_match.group(3)
                svc = current_service or "_unknown"

                rpc_id = f"proto:{filepath}:rpc:{svc}:{method_name}"
                result.nodes.append(GraphNode(
                    id=rpc_id,
                    kind=NodeKind.METHOD,
                    label=f"{svc}.{method_name}",
                    fqn=f"{svc}.{method_name}",
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=i + 1,
                    ),
                    properties={
                        "request_type": request_type,
                        "response_type": response_type,
                    },
                ))

                # RPC belongs to service
                if current_service:
                    result.edges.append(GraphEdge(
                        source=f"proto:{filepath}:service:{current_service}",
                        target=rpc_id,
                        kind=EdgeKind.CONTAINS,
                        label=f"{current_service} contains {method_name}",
                    ))

        # Messages
        for i, line in enumerate(lines):
            m = _MESSAGE_RE.search(line)
            if m:
                msg_name = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"proto:{filepath}:message:{msg_name}",
                    kind=NodeKind.PROTOCOL_MESSAGE,
                    label=msg_name,
                    fqn=msg_name,
                    module=ctx.module_name,
                    location=SourceLocation(
                        file_path=filepath,
                        line_start=i + 1,
                    ),
                ))

        return result
