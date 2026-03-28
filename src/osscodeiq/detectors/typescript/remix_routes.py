"""Remix route detector."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import GraphNode, NodeKind, SourceLocation


class RemixRouteDetector:
    """Detects Remix loader, action exports, default component exports, and data hooks."""

    name: str = "remix_routes"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    # export async function loader( or export function loader(
    _LOADER_PATTERN = re.compile(
        r"export\s+(?:async\s+)?function\s+loader\s*\("
    )

    # export async function action( or export function action(
    _ACTION_PATTERN = re.compile(
        r"export\s+(?:async\s+)?function\s+action\s*\("
    )

    # export default function ComponentName( or export default function(
    _DEFAULT_COMPONENT_PATTERN = re.compile(
        r"export\s+default\s+function\s+(\w*)\s*\("
    )

    # useLoaderData() or useActionData()
    _USE_LOADER_DATA = re.compile(r"\buseLoaderData\s*\(\s*\)")
    _USE_ACTION_DATA = re.compile(r"\buseActionData\s*\(\s*\)")

    def _derive_route_path(self, file_path: str) -> str | None:
        """Derive route path from Remix file path convention.

        app/routes/users.tsx         -> /users
        app/routes/users.$id.tsx     -> /users/:id
        app/routes/_index.tsx        -> /
        app/routes/users_.tsx        -> /users
        app/routes/blog.articles.tsx -> /blog/articles
        """
        # Only process files under app/routes/
        if "app/routes/" not in file_path:
            return None

        # Extract the route segment after app/routes/
        segment = file_path.split("app/routes/", 1)[1]

        # Remove file extension
        segment = re.sub(r"\.(tsx?|jsx?)$", "", segment)

        # Handle _index convention
        if segment == "_index" or segment.endswith("/_index"):
            prefix = segment.rsplit("_index", 1)[0].rstrip("/.")
            if not prefix:
                return "/"
            return "/" + prefix.replace(".", "/")

        # Replace Remix $ params with :param style
        parts = segment.split(".")
        path_parts = []
        for part in parts:
            if part.startswith("$"):
                path_parts.append(f":{part[1:]}")
            elif part.endswith("_"):
                # Pathless layout route - include the segment but it's a layout
                path_parts.append(part.rstrip("_"))
            else:
                path_parts.append(part)

        return "/" + "/".join(path_parts)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        route_path = self._derive_route_path(ctx.file_path)

        # Detect loader exports
        for match in self._LOADER_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"remix:{ctx.file_path}:loader:{line}"
            properties: dict = {
                "framework": "remix",
                "type": "loader",
                "http_method": "GET",
            }
            if route_path:
                properties["route_path"] = route_path

            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"loader {route_path or ctx.file_path}",
                    fqn=f"{ctx.file_path}::loader",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties=properties,
                )
            )

        # Detect action exports
        for match in self._ACTION_PATTERN.finditer(text):
            line = text[: match.start()].count("\n") + 1
            node_id = f"remix:{ctx.file_path}:action:{line}"
            properties = {
                "framework": "remix",
                "type": "action",
                "http_method": "POST",
            }
            if route_path:
                properties["route_path"] = route_path

            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"action {route_path or ctx.file_path}",
                    fqn=f"{ctx.file_path}::action",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties=properties,
                )
            )

        # Detect default component export
        for match in self._DEFAULT_COMPONENT_PATTERN.finditer(text):
            comp_name = match.group(1) or "default"
            line = text[: match.start()].count("\n") + 1
            node_id = f"remix:{ctx.file_path}:component:{comp_name}"
            properties: dict = {
                "framework": "remix",
                "type": "component",
            }
            if route_path:
                properties["route_path"] = route_path

            # Check for data hook usage
            if self._USE_LOADER_DATA.search(text):
                properties["uses_loader_data"] = True
            if self._USE_ACTION_DATA.search(text):
                properties["uses_action_data"] = True

            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=comp_name,
                    fqn=f"{ctx.file_path}::{comp_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties=properties,
                )
            )

        return result
