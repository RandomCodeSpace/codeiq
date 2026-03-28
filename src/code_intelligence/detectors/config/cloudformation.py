"""AWS CloudFormation template detector.

Detects CloudFormation resources, parameters, outputs, and cross-resource references.
"""

from __future__ import annotations

import re
from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

# Pattern for !GetAtt in text-based detection
_GETATT_RE = re.compile(r"!GetAtt\s+(\w+)\.", re.MULTILINE)
_REF_RE = re.compile(r"!Ref\s+(\w+)", re.MULTILINE)


def _is_cfn_template(data: dict[str, Any]) -> bool:
    """Check whether parsed data looks like a CloudFormation template."""
    if "AWSTemplateFormatVersion" in data:
        return True
    resources = data.get("Resources")
    if isinstance(resources, dict):
        for _key, val in resources.items():
            if isinstance(val, dict):
                rtype = val.get("Type", "")
                if isinstance(rtype, str) and rtype.startswith("AWS::"):
                    return True
    return False


def _get_data(ctx: DetectorContext) -> dict[str, Any] | None:
    """Extract data from parsed_data for YAML or JSON types."""
    if not ctx.parsed_data:
        return None

    ptype = ctx.parsed_data.get("type")
    if ptype in ("yaml", "json"):
        data = ctx.parsed_data.get("data")
        if isinstance(data, dict) and _is_cfn_template(data):
            return data
    return None


def _collect_refs(value: Any, refs: set[str]) -> None:
    """Recursively collect Ref and Fn::GetAtt references from a value tree."""
    if isinstance(value, dict):
        if "Ref" in value:
            ref = value["Ref"]
            if isinstance(ref, str):
                refs.add(ref)
        if "Fn::GetAtt" in value:
            getatt = value["Fn::GetAtt"]
            if isinstance(getatt, list) and len(getatt) >= 1:
                refs.add(str(getatt[0]))
            elif isinstance(getatt, str) and "." in getatt:
                refs.add(getatt.split(".")[0])
        for v in value.values():
            _collect_refs(v, refs)
    elif isinstance(value, list):
        for item in value:
            _collect_refs(item, refs)


class CloudFormationDetector:
    """Detects AWS CloudFormation resources, parameters, outputs, and dependencies."""

    name: str = "cloudformation"
    supported_languages: tuple[str, ...] = ("yaml", "json")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        fp = ctx.file_path

        data = _get_data(ctx)
        if not data:
            return result

        resource_ids: set[str] = set()

        # Process Resources
        resources = data.get("Resources")
        if isinstance(resources, dict):
            for logical_id, resource in sorted(resources.items()):
                if not isinstance(resource, dict):
                    continue

                resource_type = resource.get("Type", "unknown")
                node_id = f"cfn:{fp}:resource:{logical_id}"
                resource_ids.add(logical_id)

                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.INFRA_RESOURCE,
                    label=f"{logical_id} ({resource_type})",
                    fqn=f"cfn:{logical_id}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={
                        "logical_id": str(logical_id),
                        "resource_type": str(resource_type),
                    },
                ))

                # Collect Ref and Fn::GetAtt references within this resource
                refs: set[str] = set()
                _collect_refs(resource, refs)
                # Remove self-reference
                refs.discard(logical_id)

                for ref in sorted(refs):
                    result.edges.append(GraphEdge(
                        source=node_id,
                        target=f"cfn:{fp}:resource:{ref}",
                        kind=EdgeKind.DEPENDS_ON,
                        label=f"{logical_id} -> {ref}",
                        properties={"ref_type": "Ref/GetAtt"},
                    ))

        # Process Parameters
        parameters = data.get("Parameters")
        if isinstance(parameters, dict):
            for param_name, param_def in sorted(parameters.items()):
                if not isinstance(param_def, dict):
                    continue

                param_type = param_def.get("Type", "String")
                default = param_def.get("Default")
                description = param_def.get("Description", "")

                props: dict[str, Any] = {
                    "param_type": str(param_type),
                    "cfn_type": "parameter",
                }
                if default is not None:
                    props["default"] = str(default)
                if description:
                    props["description"] = str(description)

                result.nodes.append(GraphNode(
                    id=f"cfn:{fp}:parameter:{param_name}",
                    kind=NodeKind.CONFIG_DEFINITION,
                    label=f"param:{param_name}",
                    fqn=f"cfn:param:{param_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties=props,
                ))

        # Process Outputs
        outputs = data.get("Outputs")
        if isinstance(outputs, dict):
            for output_name, output_def in sorted(outputs.items()):
                if not isinstance(output_def, dict):
                    continue

                description = output_def.get("Description", "")
                props_out: dict[str, Any] = {"cfn_type": "output"}
                if description:
                    props_out["description"] = str(description)

                export = output_def.get("Export")
                if isinstance(export, dict) and "Name" in export:
                    props_out["export_name"] = str(export["Name"])

                result.nodes.append(GraphNode(
                    id=f"cfn:{fp}:output:{output_name}",
                    kind=NodeKind.CONFIG_DEFINITION,
                    label=f"output:{output_name}",
                    fqn=f"cfn:output:{output_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties=props_out,
                ))

        return result
