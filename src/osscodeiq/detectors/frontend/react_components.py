"""React component and hook detector."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import EdgeKind, GraphEdge, GraphNode, NodeKind, SourceLocation


class ReactComponentDetector:
    """Detects React function/class components, custom hooks, and child rendering."""

    name: str = "frontend.react_components"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    # Function component patterns
    _EXPORT_DEFAULT_FUNC = re.compile(
        r"export\s+default\s+function\s+([A-Z]\w*)\s*\("
    )
    _EXPORT_CONST_ARROW = re.compile(
        r"export\s+const\s+([A-Z]\w*)\s*=\s*\("
    )
    _EXPORT_CONST_FC = re.compile(
        r"export\s+const\s+([A-Z]\w*)\s*:\s*React\.FC"
    )

    # Class component patterns
    _CLASS_EXTENDS_REACT_COMPONENT = re.compile(
        r"class\s+([A-Z]\w*)\s+extends\s+React\.Component"
    )
    _CLASS_EXTENDS_COMPONENT = re.compile(
        r"class\s+([A-Z]\w*)\s+extends\s+Component\b"
    )

    # Custom hook patterns (exported functions starting with "use")
    _EXPORT_FUNC_HOOK = re.compile(
        r"export\s+function\s+(use[A-Z]\w*)\s*\("
    )
    _EXPORT_CONST_HOOK = re.compile(
        r"export\s+const\s+(use[A-Z]\w*)\s*=\s*"
    )

    # JSX child reference: <SomeComponent (uppercase tag names are components)
    _JSX_TAG = re.compile(
        r"<([A-Z]\w*)\b"
    )

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        component_names: list[str] = []

        # --- Function components ---
        for pattern in (self._EXPORT_DEFAULT_FUNC, self._EXPORT_CONST_ARROW, self._EXPORT_CONST_FC):
            for match in pattern.finditer(text):
                name = match.group(1)
                if name in [c for c, _ in [(n, None) for n in component_names]]:
                    continue
                line = text[: match.start()].count("\n") + 1
                node_id = f"react:{ctx.file_path}:component:{name}"
                result.nodes.append(
                    GraphNode(
                        id=node_id,
                        kind=NodeKind.COMPONENT,
                        label=name,
                        fqn=f"{ctx.file_path}::{name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={
                            "framework": "react",
                            "component_type": "function",
                        },
                    )
                )
                component_names.append(name)

        # --- Class components ---
        for pattern in (self._CLASS_EXTENDS_REACT_COMPONENT, self._CLASS_EXTENDS_COMPONENT):
            for match in pattern.finditer(text):
                name = match.group(1)
                if name in component_names:
                    continue
                line = text[: match.start()].count("\n") + 1
                node_id = f"react:{ctx.file_path}:component:{name}"
                result.nodes.append(
                    GraphNode(
                        id=node_id,
                        kind=NodeKind.COMPONENT,
                        label=name,
                        fqn=f"{ctx.file_path}::{name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={
                            "framework": "react",
                            "component_type": "class",
                        },
                    )
                )
                component_names.append(name)

        # --- Custom hooks ---
        hook_names: list[str] = []
        for pattern in (self._EXPORT_FUNC_HOOK, self._EXPORT_CONST_HOOK):
            for match in pattern.finditer(text):
                name = match.group(1)
                if name in hook_names:
                    continue
                line = text[: match.start()].count("\n") + 1
                node_id = f"react:{ctx.file_path}:hook:{name}"
                result.nodes.append(
                    GraphNode(
                        id=node_id,
                        kind=NodeKind.HOOK,
                        label=name,
                        fqn=f"{ctx.file_path}::{name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={
                            "framework": "react",
                        },
                    )
                )
                hook_names.append(name)

        # --- RENDERS edges (JSX child components) ---
        all_detected = set(component_names) | set(hook_names)
        child_names: set[str] = set()
        for match in self._JSX_TAG.finditer(text):
            tag = match.group(1)
            if tag not in all_detected:
                child_names.add(tag)

        for comp in component_names:
            source_id = f"react:{ctx.file_path}:component:{comp}"
            for child in sorted(child_names):
                result.edges.append(
                    GraphEdge(
                        source=source_id,
                        target=child,
                        kind=EdgeKind.RENDERS,
                        label=f"{comp} renders {child}",
                    )
                )

        return result
