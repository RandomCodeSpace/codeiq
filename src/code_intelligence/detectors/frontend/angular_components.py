"""Angular component, service, directive, pipe, and module detector."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import GraphNode, NodeKind, SourceLocation


class AngularComponentDetector:
    """Detects Angular @Component, @Injectable, @Directive, @Pipe, and @NgModule decorators."""

    name: str = "frontend.angular_components"
    supported_languages: tuple[str, ...] = ("typescript",)

    # @Component({ selector: 'app-name' }) followed by class Name
    _COMPONENT_DECORATOR = re.compile(
        r"@Component\s*\(\s*\{.*?selector\s*:\s*['\"]([^'\"]+)['\"].*?\}\s*\)\s*\n?\s*(?:export\s+)?class\s+(\w+)",
        re.DOTALL,
    )

    # @Injectable({ providedIn: 'root' }) followed by class Name
    _INJECTABLE_DECORATOR = re.compile(
        r"@Injectable\s*\(\s*\{.*?providedIn\s*:\s*['\"](\w+)['\"].*?\}\s*\)\s*\n?\s*(?:export\s+)?class\s+(\w+)",
        re.DOTALL,
    )

    # @Directive({ selector: '[appHighlight]' }) followed by class Name
    _DIRECTIVE_DECORATOR = re.compile(
        r"@Directive\s*\(\s*\{.*?selector\s*:\s*['\"]([^'\"]+)['\"].*?\}\s*\)\s*\n?\s*(?:export\s+)?class\s+(\w+)",
        re.DOTALL,
    )

    # @Pipe({ name: 'pipeName' }) followed by class Name
    _PIPE_DECORATOR = re.compile(
        r"@Pipe\s*\(\s*\{.*?name\s*:\s*['\"](\w+)['\"].*?\}\s*\)\s*\n?\s*(?:export\s+)?class\s+(\w+)",
        re.DOTALL,
    )

    # @NgModule({ declarations: [...] }) followed by class Name
    _NGMODULE_DECORATOR = re.compile(
        r"@NgModule\s*\(\s*\{.*?\}\s*\)\s*\n?\s*(?:export\s+)?class\s+(\w+)",
        re.DOTALL,
    )

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        seen: set[str] = set()

        # --- @Component ---
        for match in self._COMPONENT_DECORATOR.finditer(text):
            selector = match.group(1)
            class_name = match.group(2)
            if class_name in seen:
                continue
            seen.add(class_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"angular:{ctx.file_path}:component:{class_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "angular",
                        "selector": selector,
                        "decorator": "Component",
                    },
                )
            )

        # --- @Injectable (services) ---
        for match in self._INJECTABLE_DECORATOR.finditer(text):
            provided_in = match.group(1)
            class_name = match.group(2)
            if class_name in seen:
                continue
            seen.add(class_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"angular:{ctx.file_path}:service:{class_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.MIDDLEWARE,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "angular",
                        "provided_in": provided_in,
                        "decorator": "Injectable",
                    },
                )
            )

        # --- @Directive ---
        for match in self._DIRECTIVE_DECORATOR.finditer(text):
            selector = match.group(1)
            class_name = match.group(2)
            if class_name in seen:
                continue
            seen.add(class_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"angular:{ctx.file_path}:component:{class_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "angular",
                        "selector": selector,
                        "decorator": "Directive",
                    },
                )
            )

        # --- @Pipe ---
        for match in self._PIPE_DECORATOR.finditer(text):
            pipe_name = match.group(1)
            class_name = match.group(2)
            if class_name in seen:
                continue
            seen.add(class_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"angular:{ctx.file_path}:component:{class_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "angular",
                        "pipe_name": pipe_name,
                        "decorator": "Pipe",
                    },
                )
            )

        # --- @NgModule ---
        for match in self._NGMODULE_DECORATOR.finditer(text):
            class_name = match.group(1)
            if class_name in seen:
                continue
            seen.add(class_name)
            line = text[: match.start()].count("\n") + 1
            node_id = f"angular:{ctx.file_path}:component:{class_name}"
            result.nodes.append(
                GraphNode(
                    id=node_id,
                    kind=NodeKind.COMPONENT,
                    label=class_name,
                    fqn=f"{ctx.file_path}::{class_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=line),
                    properties={
                        "framework": "angular",
                        "decorator": "NgModule",
                    },
                )
            )

        return result
