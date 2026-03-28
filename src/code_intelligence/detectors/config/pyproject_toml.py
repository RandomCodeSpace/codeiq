"""Detector for pyproject.toml files (Python project metadata and dependencies)."""

from __future__ import annotations

import os
import sys

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

if sys.version_info >= (3, 11):
    import tomllib
else:
    try:
        import tomli as tomllib  # type: ignore[no-redef]
    except ImportError:
        tomllib = None  # type: ignore[assignment]


class PyprojectTomlDetector:
    """Detects Python project metadata, dependencies, and entry points from pyproject.toml."""

    name: str = "pyproject_toml"
    supported_languages: tuple[str, ...] = ("toml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        # Only trigger for files named exactly "pyproject.toml"
        if os.path.basename(ctx.file_path) != "pyproject.toml":
            return result

        if tomllib is None:
            return result

        try:
            data = tomllib.loads(decode_text(ctx))
        except Exception:
            return result

        if not isinstance(data, dict):
            return result

        filepath = ctx.file_path
        module_id = f"pypi:{filepath}"

        # Resolve project name from [project] or [tool.poetry]
        project_section = data.get("project", {})
        poetry_section = data.get("tool", {}).get("poetry", {})

        pkg_name = (
            project_section.get("name")
            or poetry_section.get("name")
            or filepath
        )

        # Module properties
        props: dict[str, object] = {"package_name": pkg_name}
        version = project_section.get("version") or poetry_section.get("version")
        if version:
            props["version"] = version
        description = project_section.get("description") or poetry_section.get("description")
        if description:
            props["description"] = description

        # MODULE node for the project
        result.nodes.append(GraphNode(
            id=module_id,
            kind=NodeKind.MODULE,
            label=pkg_name,
            fqn=pkg_name,
            module=ctx.module_name,
            location=SourceLocation(file_path=filepath),
            properties=props,
        ))

        # DEPENDS_ON edges for dependencies
        # PEP 621 style: [project].dependencies is a list of strings
        pep621_deps = project_section.get("dependencies", [])
        if isinstance(pep621_deps, list):
            for dep_spec in pep621_deps:
                if not isinstance(dep_spec, str):
                    continue
                # Extract package name from spec like "requests>=2.0"
                dep_name = _parse_dep_name(dep_spec)
                if dep_name:
                    result.edges.append(GraphEdge(
                        source=module_id,
                        target=f"pypi:{dep_name}",
                        kind=EdgeKind.DEPENDS_ON,
                        label=f"{pkg_name} depends on {dep_name}",
                        properties={"dep_spec": dep_spec},
                    ))

        # Poetry style: [tool.poetry].dependencies is a dict
        poetry_deps = poetry_section.get("dependencies", {})
        if isinstance(poetry_deps, dict):
            for dep_name, dep_version in poetry_deps.items():
                if not isinstance(dep_name, str):
                    continue
                # Skip python itself
                if dep_name.lower() == "python":
                    continue
                edge_props: dict[str, object] = {}
                if isinstance(dep_version, str):
                    edge_props["version_spec"] = dep_version
                result.edges.append(GraphEdge(
                    source=module_id,
                    target=f"pypi:{dep_name}",
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"{pkg_name} depends on {dep_name}",
                    properties=edge_props,
                ))

        # CONFIG_DEFINITION nodes for entry points / scripts
        scripts = project_section.get("scripts", {})
        if not isinstance(scripts, dict):
            scripts = {}
        poetry_scripts = poetry_section.get("scripts", {})
        if isinstance(poetry_scripts, dict):
            scripts.update(poetry_scripts)

        for script_name, script_target in scripts.items():
            if not isinstance(script_name, str):
                continue
            script_id = f"pypi:{filepath}:script:{script_name}"
            script_props: dict[str, object] = {"script_name": script_name}
            if isinstance(script_target, str):
                script_props["target"] = script_target

            result.nodes.append(GraphNode(
                id=script_id,
                kind=NodeKind.CONFIG_DEFINITION,
                label=script_name,
                fqn=f"{pkg_name}:script:{script_name}",
                module=ctx.module_name,
                location=SourceLocation(file_path=filepath),
                properties=script_props,
            ))

            result.edges.append(GraphEdge(
                source=module_id,
                target=script_id,
                kind=EdgeKind.CONTAINS,
                label=f"{pkg_name} defines script {script_name}",
            ))

        return result


def _parse_dep_name(spec: str) -> str | None:
    """Extract package name from a PEP 508 dependency specifier."""
    # e.g. "requests>=2.0", "numpy", "black[jupyter]>=22.0"
    spec = spec.strip()
    if not spec:
        return None
    # Split on version specifiers or extras
    for ch in ">=<![;@ ":
        idx = spec.find(ch)
        if idx > 0:
            spec = spec[:idx]
    return spec.strip() or None
