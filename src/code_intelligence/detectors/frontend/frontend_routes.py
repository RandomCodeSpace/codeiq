"""Frontend route detector for React Router, Vue Router, Next.js, and Angular."""

from __future__ import annotations

import re
from pathlib import PurePosixPath

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class FrontendRouteDetector:
    """Detects frontend routing definitions across major frameworks."""

    name: str = "frontend.frontend_routes"
    supported_languages: tuple[str, ...] = ("typescript", "javascript", "vue", "svelte")

    # --- React Router patterns ---
    # <Route path="/foo" component={Bar}> or <Route path="/foo" element={<Bar />}>
    _REACT_ROUTE_COMPONENT = re.compile(
        r"<Route\s+[^>]*?path\s*=\s*[\"']([^\"']+)[\"'][^>]*?"
        r"component\s*=\s*\{(\w+)\}",
    )
    _REACT_ROUTE_ELEMENT = re.compile(
        r"<Route\s+[^>]*?path\s*=\s*[\"']([^\"']+)[\"'][^>]*?"
        r"element\s*=\s*\{<(\w+)",
    )
    # <Route path="/foo"> (no component/element yet, or nested)
    _REACT_ROUTE_BARE = re.compile(
        r"<Route\s+[^>]*?path\s*=\s*[\"']([^\"']+)[\"']",
    )

    # --- Vue Router patterns ---
    # { path: '/foo', component: Bar } or { path: '/foo', component: () => import(...) }
    _VUE_ROUTE = re.compile(
        r"\{\s*path\s*:\s*['\"]([^'\"]+)['\"]"
        r"(?:.*?component\s*:\s*(\w+))?"
    )
    _VUE_CREATE_ROUTER = re.compile(r"createRouter\s*\(")
    _VUE_ROUTES_ARRAY = re.compile(r"\broutes\s*:\s*\[")

    # --- Angular patterns ---
    _ANGULAR_ROUTE = re.compile(
        r"\{\s*path\s*:\s*['\"]([^'\"]+)['\"]"
        r"(?:.*?component\s*:\s*(\w+))?"
    )
    _ANGULAR_ROUTER_MODULE = re.compile(
        r"RouterModule\.for(?:Root|Child)\s*\("
    )

    # --- Next.js file-based routing ---
    # pages/index.tsx, pages/about.tsx, pages/users/[id].tsx
    _NEXTJS_PAGES = re.compile(r"^pages/(.+)\.(tsx|ts|jsx|js)$")
    # app/**/page.tsx (App Router)
    _NEXTJS_APP = re.compile(r"^app/(.+)/page\.(tsx|ts|jsx|js)$")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        self._detect_nextjs_file_routes(ctx, result)
        self._detect_react_router(ctx, text, result)
        self._detect_vue_router(ctx, text, result)
        self._detect_angular_router(ctx, text, result)

        return result

    def _detect_nextjs_file_routes(
        self, ctx: DetectorContext, result: DetectorResult
    ) -> None:
        """Detect Next.js file-based routes from file path alone."""
        fp = ctx.file_path

        match = self._NEXTJS_PAGES.match(fp)
        if match:
            raw = match.group(1)
            route_path = self._nextjs_pages_path(raw)
            node_id = f"route:{fp}:nextjs:{route_path}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"page {route_path}",
                    fqn=f"{fp}::page",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=1),
                    properties={
                        "protocol": "frontend_route",
                        "framework": "nextjs",
                        "route_path": route_path,
                    },
                )
            )
            return

        match = self._NEXTJS_APP.match(fp)
        if match:
            raw = match.group(1)
            route_path = "/" + raw.replace("\\", "/")
            node_id = f"route:{fp}:nextjs:{route_path}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"page {route_path}",
                    fqn=f"{fp}::page",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=1),
                    properties={
                        "protocol": "frontend_route",
                        "framework": "nextjs",
                        "route_path": route_path,
                    },
                )
            )

    @staticmethod
    def _nextjs_pages_path(raw: str) -> str:
        """Convert a pages-directory relative path to a route path."""
        # pages/index -> /
        # pages/about -> /about
        # pages/users/[id] -> /users/[id]
        parts = raw.replace("\\", "/").split("/")
        # Remove trailing 'index'
        if parts and parts[-1] == "index":
            parts = parts[:-1]
        route = "/" + "/".join(parts) if parts else "/"
        return route

    def _detect_react_router(
        self, ctx: DetectorContext, text: str, result: DetectorResult
    ) -> None:
        """Detect React Router route definitions."""
        seen_paths: set[str] = set()

        # <Route path="..." component={Comp}>
        for match in self._REACT_ROUTE_COMPONENT.finditer(text):
            path = match.group(1)
            component = match.group(2)
            if path in seen_paths:
                continue
            seen_paths.add(path)
            line = text[: match.start()].count("\n") + 1
            node_id = f"route:{ctx.file_path}:react:{path}"
            result.nodes.append(self._route_node(
                node_id, path, "react", ctx, line,
            ))
            # RENDERS edge to component
            result.edges.append(GraphEdge(
                source=node_id,
                target=component,
                kind=EdgeKind.RENDERS,
                label=f"renders {component}",
            ))

        # <Route path="..." element={<Comp />}>
        for match in self._REACT_ROUTE_ELEMENT.finditer(text):
            path = match.group(1)
            component = match.group(2)
            if path in seen_paths:
                continue
            seen_paths.add(path)
            line = text[: match.start()].count("\n") + 1
            node_id = f"route:{ctx.file_path}:react:{path}"
            result.nodes.append(self._route_node(
                node_id, path, "react", ctx, line,
            ))
            result.edges.append(GraphEdge(
                source=node_id,
                target=component,
                kind=EdgeKind.RENDERS,
                label=f"renders {component}",
            ))

        # Bare <Route path="..."> (no component/element captured above)
        for match in self._REACT_ROUTE_BARE.finditer(text):
            path = match.group(1)
            if path in seen_paths:
                continue
            seen_paths.add(path)
            line = text[: match.start()].count("\n") + 1
            node_id = f"route:{ctx.file_path}:react:{path}"
            result.nodes.append(self._route_node(
                node_id, path, "react", ctx, line,
            ))

    def _detect_vue_router(
        self, ctx: DetectorContext, text: str, result: DetectorResult
    ) -> None:
        """Detect Vue Router route definitions."""
        has_create_router = bool(self._VUE_CREATE_ROUTER.search(text))
        has_routes_array = bool(self._VUE_ROUTES_ARRAY.search(text))

        if not (has_create_router or has_routes_array):
            return

        for match in self._VUE_ROUTE.finditer(text):
            path = match.group(1)
            component = match.group(2)
            line = text[: match.start()].count("\n") + 1
            node_id = f"route:{ctx.file_path}:vue:{path}"
            result.nodes.append(self._route_node(
                node_id, path, "vue", ctx, line,
            ))
            if component:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=component,
                    kind=EdgeKind.RENDERS,
                    label=f"renders {component}",
                ))

    def _detect_angular_router(
        self, ctx: DetectorContext, text: str, result: DetectorResult
    ) -> None:
        """Detect Angular Router route definitions."""
        has_router_module = bool(self._ANGULAR_ROUTER_MODULE.search(text))

        if not has_router_module:
            return

        for match in self._ANGULAR_ROUTE.finditer(text):
            path = match.group(1)
            component = match.group(2)
            line = text[: match.start()].count("\n") + 1
            node_id = f"route:{ctx.file_path}:angular:{path}"
            result.nodes.append(self._route_node(
                node_id, path, "angular", ctx, line,
            ))
            if component:
                result.edges.append(GraphEdge(
                    source=node_id,
                    target=component,
                    kind=EdgeKind.RENDERS,
                    label=f"renders {component}",
                ))

    @staticmethod
    def _route_node(
        node_id: str,
        path: str,
        framework: str,
        ctx: DetectorContext,
        line: int,
    ) -> GraphNode:
        return GraphNode(
            id=node_id,
            kind=NodeKind.ENDPOINT,
            label=f"route {path}",
            fqn=f"{ctx.file_path}::route:{path}",
            module=ctx.module_name,
            location=SourceLocation(file_path=ctx.file_path, line_start=line),
            properties={
                "protocol": "frontend_route",
                "framework": framework,
                "route_path": path,
            },
        )
