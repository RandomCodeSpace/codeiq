"""GitHub Actions workflow detector for CI/CD pipeline definitions."""

from __future__ import annotations

from typing import Any

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)


def _is_github_actions_file(ctx: DetectorContext) -> bool:
    """Check whether the file is a GitHub Actions workflow."""
    return ".github/workflows/" in ctx.file_path


class GitHubActionsDetector:
    """Detects workflows, jobs, triggers, and job dependencies from GitHub Actions YAML files."""

    name: str = "github_actions"
    supported_languages: tuple[str, ...] = ("yaml",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()

        if not _is_github_actions_file(ctx):
            return result

        if not ctx.parsed_data:
            return result

        data = ctx.parsed_data.get("data")
        if not isinstance(data, dict):
            return result

        fp = ctx.file_path
        workflow_id = f"gha:{fp}"

        # Workflow MODULE node
        workflow_name = data.get("name", fp)
        result.nodes.append(GraphNode(
            id=workflow_id,
            kind=NodeKind.MODULE,
            label=str(workflow_name),
            fqn=workflow_id,
            module=ctx.module_name,
            location=SourceLocation(file_path=fp),
            properties={"workflow_file": fp},
        ))

        # Trigger events from "on:" key
        on_triggers = data.get("on") or data.get(True)  # YAML parses bare "on" as True
        if on_triggers is not None:
            if isinstance(on_triggers, str):
                # Simple form: on: push
                result.nodes.append(GraphNode(
                    id=f"gha:{fp}:trigger:{on_triggers}",
                    kind=NodeKind.CONFIG_KEY,
                    label=f"trigger: {on_triggers}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp),
                    properties={"event": on_triggers},
                ))
            elif isinstance(on_triggers, list):
                # List form: on: [push, pull_request]
                for event in on_triggers:
                    event_str = str(event)
                    result.nodes.append(GraphNode(
                        id=f"gha:{fp}:trigger:{event_str}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"trigger: {event_str}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties={"event": event_str},
                    ))
            elif isinstance(on_triggers, dict):
                # Dict form: on: { push: {branches: [main]}, ... }
                for event_name, event_config in on_triggers.items():
                    event_str = str(event_name)
                    props: dict[str, Any] = {"event": event_str}
                    if isinstance(event_config, dict):
                        props["config"] = event_config
                    result.nodes.append(GraphNode(
                        id=f"gha:{fp}:trigger:{event_str}",
                        kind=NodeKind.CONFIG_KEY,
                        label=f"trigger: {event_str}",
                        module=ctx.module_name,
                        location=SourceLocation(file_path=fp),
                        properties=props,
                    ))

        # Jobs
        jobs = data.get("jobs")
        if not isinstance(jobs, dict):
            return result

        job_ids: dict[str, str] = {}
        for job_name in jobs:
            job_ids[job_name] = f"gha:{fp}:job:{job_name}"

        for job_name, job_def in jobs.items():
            if not isinstance(job_def, dict):
                continue

            job_id = job_ids[job_name]

            props = {}
            runs_on = job_def.get("runs-on")
            if runs_on is not None:
                props["runs_on"] = runs_on if isinstance(runs_on, str) else str(runs_on)

            result.nodes.append(GraphNode(
                id=job_id,
                kind=NodeKind.METHOD,
                label=job_def.get("name", job_name),
                fqn=job_id,
                module=ctx.module_name,
                location=SourceLocation(file_path=fp),
                properties=props,
            ))

            # CONTAINS edge: workflow -> job
            result.edges.append(GraphEdge(
                source=workflow_id,
                target=job_id,
                kind=EdgeKind.CONTAINS,
                label=f"workflow contains job {job_name}",
            ))

            # Job dependencies via "needs"
            needs = job_def.get("needs")
            if isinstance(needs, str):
                needs = [needs]
            if isinstance(needs, list):
                for dep in needs:
                    dep_str = str(dep)
                    if dep_str in job_ids:
                        result.edges.append(GraphEdge(
                            source=job_id,
                            target=job_ids[dep_str],
                            kind=EdgeKind.DEPENDS_ON,
                            label=f"job {job_name} needs {dep_str}",
                        ))

        return result
