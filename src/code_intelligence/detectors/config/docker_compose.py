"""Docker Compose detector for container orchestration definitions."""

from __future__ import annotations

import os
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

_COMPOSE_FILENAME_RE = re.compile(
    r"^(docker-compose|compose).*\.(yml|yaml)$", re.IGNORECASE
)


def _is_compose_file(ctx: DetectorContext) -> bool:
    """Check whether the file is a Docker Compose file."""
    basename = os.path.basename(ctx.file_path)
    if _COMPOSE_FILENAME_RE.match(basename):
        return True
    # Fallback: check parsed data for compose-like structure
    if ctx.parsed_data and ctx.parsed_data.get("type") == "yaml":
        data = ctx.parsed_data.get("data")
        if isinstance(data, dict) and "services" in data:
            return True
    return False


class DockerComposeDetector:
    """Detects services, ports, volumes, networks, and dependencies from Docker Compose files."""

    name: str = "docker_compose"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        if not _is_compose_file(ctx):
            return result

        if not ctx.parsed_data:
            return result

        data = ctx.parsed_data.get("data")
        if not isinstance(data, dict):
            return result

        services = data.get("services")
        if not isinstance(services, dict):
            return result

        fp = ctx.file_path

        # Build a set of known service IDs for edge resolution
        service_ids: dict[str, str] = {}
        for svc_name in services:
            service_ids[svc_name] = f"compose:{fp}:service:{svc_name}"

        for svc_name, svc_def in services.items():
            if not isinstance(svc_def, dict):
                continue

            svc_id = service_ids[svc_name]

            # Properties for the service node
            props: dict[str, Any] = {}
            if "image" in svc_def:
                props["image"] = str(svc_def["image"])
            build = svc_def.get("build")
            if isinstance(build, str):
                props["build_context"] = build
            elif isinstance(build, dict) and "context" in build:
                props["build_context"] = str(build["context"])

            # INFRA_RESOURCE node for the service
            result.nodes.append(GraphNode(
                id=svc_id,
                kind=NodeKind.INFRA_RESOURCE,
                label=svc_name,
                fqn=f"compose:{svc_name}",
                module=ctx.module_name,
                location=SourceLocation(file_path=fp),
                properties=props,
            ))

            # Ports
            ports = svc_def.get("ports")
            if isinstance(ports, list):
                for port_entry in ports:
                    port_str = str(port_entry)
                    result.nodes.append(GraphNode(
                        id=f"compose:{fp}:service:{svc_name}:port:{port_str}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"{svc_name} port {port_str}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties={"port": port_str},
                    ))

            # depends_on
            depends_on = svc_def.get("depends_on")
            if isinstance(depends_on, list):
                for dep in depends_on:
                    dep_str = str(dep)
                    if dep_str in service_ids:
                        result.edges.append(GraphEdge(
                            source=svc_id,
                            target=service_ids[dep_str],
                            kind=EdgeKind.DEPENDS_ON,
                            label=f"{svc_name} depends on {dep_str}",
                        ))
            elif isinstance(depends_on, dict):
                for dep_str in depends_on:
                    if dep_str in service_ids:
                        result.edges.append(GraphEdge(
                            source=svc_id,
                            target=service_ids[dep_str],
                            kind=EdgeKind.DEPENDS_ON,
                            label=f"{svc_name} depends on {dep_str}",
                        ))

            # links
            links = svc_def.get("links")
            if isinstance(links, list):
                for link in links:
                    link_name = str(link).split(":")[0]
                    if link_name in service_ids:
                        result.edges.append(GraphEdge(
                            source=svc_id,
                            target=service_ids[link_name],
                            kind=EdgeKind.CONNECTS_TO,
                            label=f"{svc_name} links to {link_name}",
                        ))

            # Volumes
            volumes = svc_def.get("volumes")
            if isinstance(volumes, list):
                for vol_entry in volumes:
                    vol_str = str(vol_entry) if not isinstance(vol_entry, dict) else vol_entry.get("source", str(vol_entry))
                    result.nodes.append(GraphNode(
                        id=f"compose:{fp}:service:{svc_name}:volume:{vol_str}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"{svc_name} volume {vol_str}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties={"volume": vol_str},
                    ))

            # Networks
            networks = svc_def.get("networks")
            if isinstance(networks, list):
                for net in networks:
                    result.nodes.append(GraphNode(
                        id=f"compose:{fp}:service:{svc_name}:network:{net}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"{svc_name} network {net}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties={"network": str(net)},
                    ))
            elif isinstance(networks, dict):
                for net_name in networks:
                    result.nodes.append(GraphNode(
                        id=f"compose:{fp}:service:{svc_name}:network:{net_name}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"{svc_name} network {net_name}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties={"network": str(net_name)},
                    ))

        return result
