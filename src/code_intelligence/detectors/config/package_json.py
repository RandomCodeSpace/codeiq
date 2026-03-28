"""Detector for package.json files (npm/Node.js dependencies and scripts)."""

from __future__ import annotations

import os

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


class PackageJsonDetector:
    """Detects module dependencies and scripts from package.json files."""

    name: str = "package_json"
    supported_languages: tuple[str, ...] = ("json",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        # Only trigger for files named exactly "package.json"
        if os.path.basename(ctx.file_path) != "package.json":
            return result

        data = ctx.parsed_data
        if not isinstance(data, dict) or not isinstance(data.get("data"), dict):
            return result

        pkg = data["data"]
        filepath = ctx.file_path
        module_id = f"npm:{filepath}"
        pkg_name = pkg.get("name") or filepath

        # MODULE node for the package
        props: dict[str, object] = {"package_name": pkg_name}
        version = pkg.get("version")
        if version:
            props["version"] = version

        result.nodes.append(GraphNode(
            id=module_id,
            kind=NodeKind.MODULE,
            label=pkg_name,
            fqn=pkg_name,
            module=ctx.module_name,
            location=SourceLocation(file_path=filepath),
            properties=props,
        ))

        # DEPENDS_ON edges for dependencies and devDependencies
        for dep_key in ("dependencies", "devDependencies"):
            deps = pkg.get(dep_key)
            if not isinstance(deps, dict):
                continue
            for dep_name, dep_version in deps.items():
                if not isinstance(dep_name, str):
                    continue
                edge_props: dict[str, object] = {"dep_type": dep_key}
                if isinstance(dep_version, str):
                    edge_props["version_spec"] = dep_version
                result.edges.append(GraphEdge(
                    source=module_id,
                    target=f"npm:{dep_name}",
                    kind=EdgeKind.DEPENDS_ON,
                    label=f"{pkg_name} depends on {dep_name}",
                    properties=edge_props,
                ))

        # METHOD nodes for each script
        scripts = pkg.get("scripts")
        if isinstance(scripts, dict):
            for script_name, script_cmd in scripts.items():
                if not isinstance(script_name, str):
                    continue
                script_id = f"npm:{filepath}:script:{script_name}"
                script_props: dict[str, object] = {"script_name": script_name}
                if isinstance(script_cmd, str):
                    script_props["command"] = script_cmd
                result.nodes.append(GraphNode(
                    id=script_id,
                    kind=NodeKind.METHOD,
                    label=f"npm run {script_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=filepath),
                    properties=script_props,
                ))
                result.edges.append(GraphEdge(
                    source=module_id,
                    target=script_id,
                    kind=EdgeKind.CONTAINS,
                    label=f"{pkg_name} contains script {script_name}",
                ))

        return result
