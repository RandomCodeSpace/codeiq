"""Vue component and composable detector."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


class VueComponentDetector:
    """Detects Vue components (Options API, Composition API, <script setup>) and composables."""

    name: str = "frontend.vue_components"
    supported_languages: tuple[str, ...] = ("typescript", "javascript", "vue")

    # defineComponent with name property
    _DEFINE_COMPONENT_NAME = re.compile(
        r"export\s+default\s+defineComponent\s*\(\s*\{[^}]*?name\s*:\s*['\"](\w+)['\"]",
        re.DOTALL,
    )

    # Options API: export default { name: 'CompName'
    _OPTIONS_API_NAME = re.compile(
        r"export\s+default\s+\{\s*name\s*:\s*['\"](\w+)['\"]"
    )

    # <script setup> block detection
    _SCRIPT_SETUP = re.compile(
        r"<script\s+setup(?:\s+lang\s*=\s*['\"](?:ts|js)['\"])?\s*>"
    )

    # Composable patterns (functions starting with "use")
    _EXPORT_FUNC_COMPOSABLE = re.compile(
        r"export\s+function\s+(use[A-Z]\w*)\s*\("
    )
    _EXPORT_CONST_COMPOSABLE = re.compile(
        r"export\s+const\s+(use[A-Z]\w*)\s*=\s*"
    )

    def _extract_script_setup_name(self, file_path: str) -> str | None:
        """Derive component name from file path for <script setup> SFCs."""
        # e.g. "components/UserProfile.vue" -> "UserProfile"
        parts = file_path.replace("\\", "/").split("/")
        filename = parts[-1]
        if filename.endswith(".vue"):
            return filename[: -len(".vue")]
        return None

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)

        component_names: list[str] = []

        # --- defineComponent with name ---
        for match in self._DEFINE_COMPONENT_NAME.finditer(text):
            name = match.group(1)
            if name in component_names:
                continue
            line = text[: match.start()].count("\n") + 1
            node_id = f"vue:{ctx.file_path}:component:{name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=name,
                    fqn=f"{ctx.file_path}::{name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "vue",
                        "api_style": "composition",
                    },
                )
            )
            component_names.append(name)

        # --- Options API ---
        for match in self._OPTIONS_API_NAME.finditer(text):
            name = match.group(1)
            if name in component_names:
                continue
            line = text[: match.start()].count("\n") + 1
            node_id = f"vue:{ctx.file_path}:component:{name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=name,
                    fqn=f"{ctx.file_path}::{name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "vue",
                        "api_style": "options",
                    },
                )
            )
            component_names.append(name)

        # --- <script setup> ---
        for match in self._SCRIPT_SETUP.finditer(text):
            comp_name = self._extract_script_setup_name(ctx.file_path)
            if comp_name is None or comp_name in component_names:
                continue
            line = text[: match.start()].count("\n") + 1
            node_id = f"vue:{ctx.file_path}:component:{comp_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=comp_name,
                    fqn=f"{ctx.file_path}::{comp_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "vue",
                        "api_style": "script_setup",
                    },
                )
            )
            component_names.append(comp_name)

        # --- Composables (hooks) ---
        hook_names: list[str] = []
        for pattern in (self._EXPORT_FUNC_COMPOSABLE, self._EXPORT_CONST_COMPOSABLE):
            for match in pattern.finditer(text):
                name = match.group(1)
                if name in hook_names:
                    continue
                line = text[: match.start()].count("\n") + 1
                node_id = f"vue:{ctx.file_path}:hook:{name}"
                result.nodes.append(
                    GraphNode(
                        id=node_id,
                        kind=NodeKind.HOOK,
                        label=name,
                        fqn=f"{ctx.file_path}::{name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=ctx.file_path, line_start=line),
                        properties={
                            "framework": "vue",
                        },
                    )
                )
                hook_names.append(name)

        return result
