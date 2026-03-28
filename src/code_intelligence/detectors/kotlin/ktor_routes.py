"""Ktor route detector for Kotlin."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphEdge, GraphNode, NodeKind, SourceLocation


class KtorRouteDetector:
    """Detects Ktor route definitions, routing blocks, install plugins, and authenticate guards."""

    name: str = "ktor_routes"
    supported_languages: tuple[str, ...] = ("kotlin",)

    # get("/path") { ... } or post("/path") { ... }
    _ENDPOINT_PATTERN = re.compile(
        r"\b(get|post|put|delete|patch)\(\s*\"([^\"]+)\"\s*\)\s*\{"
    )

    # routing { ... } block
    _ROUTING_PATTERN = re.compile(r"\brouting\s*\{")

    # route("/prefix") { ... } for nested route prefixes
    _ROUTE_PREFIX_PATTERN = re.compile(
        r"\broute\(\s*\"([^\"]+)\"\s*\)\s*\{"
    )

    # install(FeatureName) { ... }
    _INSTALL_PATTERN = re.compile(
        r"\binstall\(\s*(\w+)\s*\)"
    )

    # authenticate("auth-name") { ... }
    _AUTHENTICATE_PATTERN = re.compile(
        r"\bauthenticate\(\s*\"([^\"]+)\"\s*\)\s*\{"
    )

    def _build_prefix_map(self, text: str) -> dict[int, str]:
        """Build a map of line numbers to their accumulated route prefixes.

        Tracks route() nesting by scanning open/close braces within route blocks.
        """
        prefixes: dict[int, str] = {}
        active_prefixes: list[tuple[str, int]] = []  # (prefix, brace_depth)
        brace_depth = 0

        for i, line in enumerate(text.split("\n"), 1):
            # Track brace depth
            brace_depth += line.count("{") - line.count("}")

            # Check for route prefix opening
            route_match = self._ROUTE_PREFIX_PATTERN.search(line)
            if route_match:
                active_prefixes.append((route_match.group(1), brace_depth))

            # Remove prefixes whose scope has closed
            while active_prefixes and brace_depth < active_prefixes[-1][1]:
                active_prefixes.pop()

            # Build the combined prefix for this line
            if active_prefixes:
                prefixes[i] = "".join(p for p, _ in active_prefixes)

        return prefixes

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        prefix_map = self._build_prefix_map(text)

        # Detect routing { } blocks as MODULE nodes
        for match in self._ROUTING_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"ktor:{ctx.file_path}:routing:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.MODULE,
                    label="routing",
                    fqn=f"{ctx.file_path}::routing",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "ktor",
                        "type": "router",
                    },
                )
            )

        # Detect endpoint definitions: get("/path") { ... }
        for match in self._ENDPOINT_PATTERN.finditer(text):
            method = match.group(1).upper()
            raw_path = match.group(2)
            line = text[: match.start()].count("\n") + 1

            # Prepend any accumulated route prefix
            prefix = prefix_map.get(line, "")
            path = prefix + raw_path

            node_id = f"ktor:{ctx.file_path}:{method}:{path}:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"{method} {path}",
                    fqn=f"{ctx.file_path}::{method}:{path}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "protocol": "REST",
                        "http_method": method,
                        "path_pattern": path,
                        "framework": "ktor",
                    },
                )
            )

        # Detect install(Feature) as MIDDLEWARE
        for match in self._INSTALL_PATTERN.finditer(text):
            feature_name = match.group(1)
            line = text[: match.start()].count("\n") + 1

            node_id = f"ktor:{ctx.file_path}:install:{feature_name}:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.MIDDLEWARE,
                    label=f"install:{feature_name}",
                    fqn=f"{ctx.file_path}::install:{feature_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "ktor",
                        "feature": feature_name,
                    },
                )
            )

        # Detect authenticate("name") { ... } as GUARD
        for match in self._AUTHENTICATE_PATTERN.finditer(text):
            auth_name = match.group(1)
            line = text[: match.start()].count("\n") + 1

            node_id = f"ktor:{ctx.file_path}:auth:{auth_name}:{line}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.GUARD,
                    label=f"authenticate:{auth_name}",
                    fqn=f"{ctx.file_path}::authenticate:{auth_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "ktor",
                        "auth_name": auth_name,
                    },
                )
            )

        return result
