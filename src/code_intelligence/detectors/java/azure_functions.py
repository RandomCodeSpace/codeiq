"""Azure Functions trigger and binding detector for Java, C#, and TypeScript/JavaScript."""

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

# Java/C# annotation patterns
_FUNCTION_NAME_RE = re.compile(r'@FunctionName\s*\(\s*"([^"]+)"')
_HTTP_TRIGGER_RE = re.compile(r'@HttpTrigger\s*\(')
_SB_QUEUE_RE = re.compile(r'@ServiceBusQueueTrigger\s*\([^)]*queueName\s*=\s*"([^"]+)"')
_SB_TOPIC_RE = re.compile(r'@ServiceBusTopicTrigger\s*\([^)]*topicName\s*=\s*"([^"]+)"')
_EH_TRIGGER_RE = re.compile(r'@EventHubTrigger\s*\([^)]*eventHubName\s*=\s*"([^"]+)"')
_TIMER_RE = re.compile(r'@TimerTrigger\s*\([^)]*schedule\s*=\s*"([^"]+)"')
_COSMOS_TRIGGER_RE = re.compile(r'@CosmosDB(?:Trigger|Input|Output)\s*\(')

# TypeScript/JavaScript v4 programming model patterns
_TS_FUNC_RE = re.compile(
    r"app\.(http|serviceBusQueue|serviceBusTopic|eventHub|timer|cosmosDB)\s*\(\s*['\"]([^'\"]+)['\"]"
)

_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")


class AzureFunctionsDetector:
    """Detects Azure Functions triggers and bindings in Java, C#, TypeScript, and JavaScript."""

    name: str = "azure_functions"
    supported_languages: tuple[str, ...] = ("java", "csharp", "typescript", "javascript")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = ctx.content.decode("utf-8", errors="replace")

        # Fast bail
        if (
            "FunctionName" not in text
            and "@FunctionName" not in text
            and "@HttpTrigger" not in text
            and "app.http" not in text
            and "app.serviceBus" not in text
            and "app.eventHub" not in text
            and "app.timer" not in text
            and "app.cosmosDB" not in text
        ):
            return result

        lines = text.split("\n")

        # Detect Java/C# annotation-based functions
        self._detect_annotation_functions(ctx, lines, text, result)

        # Detect TypeScript/JavaScript v4 programming model functions
        self._detect_ts_functions(ctx, lines, text, result)

        return result

    def _detect_annotation_functions(
        self,
        ctx: DetectorContext,
        lines: list[str],
        text: str,
        result: DetectorResult,
    ) -> None:
        """Detect Java/C# Azure Functions via annotations."""
        # Find class name for edge source
        class_name: str | None = None
        for line in lines:
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                break

        class_node_id = f"{ctx.file_path}:{class_name}" if class_name else ctx.file_path

        # Scan for @FunctionName annotations and their associated triggers
        for i, line in enumerate(lines):
            fn_match = _FUNCTION_NAME_RE.search(line)
            if not fn_match:
                continue

            func_name = fn_match.group(1)
            func_node_id = f"azure:func:{func_name}"

            # Look ahead for trigger annotations (within the next several lines)
            trigger_type = "unknown"
            properties: dict[str, Any] = {"trigger_type": trigger_type}
            context_lines = "\n".join(lines[i : min(i + 15, len(lines))])

            if _HTTP_TRIGGER_RE.search(context_lines):
                trigger_type = "http"
                properties["trigger_type"] = trigger_type

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@HttpTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)

                # Also create an ENDPOINT node
                endpoint_id = f"azure:func:{func_name}:endpoint"
                endpoint_node = GraphNode(
                    id=endpoint_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"HTTP {func_name}",
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    properties={"http_trigger": True, "function_name": func_name},
                )
                result.nodes.append(endpoint_node)

                result.edges.append(GraphEdge(
                    source=func_node_id,
                    target=endpoint_id,
                    kind=EdgeKind.EXPOSES,
                    label=f"{func_name} exposes HTTP endpoint",
                ))
                continue

            sb_queue_match = _SB_QUEUE_RE.search(context_lines)
            if sb_queue_match:
                queue_name = sb_queue_match.group(1)
                trigger_type = "serviceBusQueue"
                properties["trigger_type"] = trigger_type
                properties["queue_name"] = queue_name

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@ServiceBusQueueTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)

                queue_node_id = f"azure:servicebus:queue:{queue_name}"
                result.nodes.append(GraphNode(
                    id=queue_node_id,
                    kind=NodeKind.QUEUE,
                    label=f"servicebus:{queue_name}",
                    properties={"broker": "azure_servicebus", "queue": queue_name},
                ))

                result.edges.append(GraphEdge(
                    source=queue_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"queue {queue_name} triggers {func_name}",
                ))
                continue

            sb_topic_match = _SB_TOPIC_RE.search(context_lines)
            if sb_topic_match:
                topic_name = sb_topic_match.group(1)
                trigger_type = "serviceBusTopic"
                properties["trigger_type"] = trigger_type
                properties["topic_name"] = topic_name

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@ServiceBusTopicTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)

                topic_node_id = f"azure:servicebus:topic:{topic_name}"
                result.nodes.append(GraphNode(
                    id=topic_node_id,
                    kind=NodeKind.TOPIC,
                    label=f"servicebus:{topic_name}",
                    properties={"broker": "azure_servicebus", "topic": topic_name},
                ))

                result.edges.append(GraphEdge(
                    source=topic_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"topic {topic_name} triggers {func_name}",
                ))
                continue

            eh_match = _EH_TRIGGER_RE.search(context_lines)
            if eh_match:
                hub_name = eh_match.group(1)
                trigger_type = "eventHub"
                properties["trigger_type"] = trigger_type
                properties["event_hub_name"] = hub_name

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@EventHubTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)

                topic_node_id = f"azure:eventhub:{hub_name}"
                result.nodes.append(GraphNode(
                    id=topic_node_id,
                    kind=NodeKind.TOPIC,
                    label=f"eventhub:{hub_name}",
                    properties={"broker": "azure_eventhub", "event_hub": hub_name},
                ))

                result.edges.append(GraphEdge(
                    source=topic_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"event hub {hub_name} triggers {func_name}",
                ))
                continue

            timer_match = _TIMER_RE.search(context_lines)
            if timer_match:
                schedule = timer_match.group(1)
                trigger_type = "timer"
                properties["trigger_type"] = trigger_type
                properties["schedule"] = schedule

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@TimerTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)
                continue

            cosmos_match = _COSMOS_TRIGGER_RE.search(context_lines)
            if cosmos_match:
                trigger_type = "cosmosDB"
                properties["trigger_type"] = trigger_type

                func_node = GraphNode(
                    id=func_node_id,
                    kind=NodeKind.AZURE_FUNCTION,
                    label=func_name,
                    fqn=f"{class_name}.{func_name}" if class_name else func_name,
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    annotations=["@FunctionName", "@CosmosDBTrigger"],
                    properties=properties,
                )
                result.nodes.append(func_node)

                resource_node_id = f"azure:cosmos:func:{func_name}"
                result.nodes.append(GraphNode(
                    id=resource_node_id,
                    kind=NodeKind.AZURE_RESOURCE,
                    label=f"cosmosdb:{func_name}",
                    properties={"cosmos_type": "trigger", "function_name": func_name},
                ))

                result.edges.append(GraphEdge(
                    source=resource_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"CosmosDB triggers {func_name}",
                ))
                continue

            # No specific trigger found, still record the function
            func_node = GraphNode(
                id=func_node_id,
                kind=NodeKind.AZURE_FUNCTION,
                label=func_name,
                fqn=f"{class_name}.{func_name}" if class_name else func_name,
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                annotations=["@FunctionName"],
                properties=properties,
            )
            result.nodes.append(func_node)

    def _detect_ts_functions(
        self,
        ctx: DetectorContext,
        lines: list[str],
        text: str,
        result: DetectorResult,
    ) -> None:
        """Detect TypeScript/JavaScript v4 programming model Azure Functions."""
        _trigger_type_map = {
            "http": "http",
            "serviceBusQueue": "serviceBusQueue",
            "serviceBusTopic": "serviceBusTopic",
            "eventHub": "eventHub",
            "timer": "timer",
            "cosmosDB": "cosmosDB",
        }

        for i, line in enumerate(lines):
            m = _TS_FUNC_RE.search(line)
            if not m:
                continue

            app_method = m.group(1)
            func_name = m.group(2)
            trigger_type = _trigger_type_map.get(app_method, app_method)
            func_node_id = f"azure:func:{func_name}"

            properties: dict[str, Any] = {"trigger_type": trigger_type}

            func_node = GraphNode(
                id=func_node_id,
                kind=NodeKind.AZURE_FUNCTION,
                label=func_name,
                module=ctx.module_name,
                location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                properties=properties,
            )
            result.nodes.append(func_node)

            # Create additional nodes/edges depending on trigger type
            if trigger_type == "http":
                endpoint_id = f"azure:func:{func_name}:endpoint"
                endpoint_node = GraphNode(
                    id=endpoint_id,
                    kind=NodeKind.ENDPOINT,
                    label=f"HTTP {func_name}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=ctx.file_path, line_start=i + 1),
                    properties={"http_trigger": True, "function_name": func_name},
                )
                result.nodes.append(endpoint_node)
                result.edges.append(GraphEdge(
                    source=func_node_id,
                    target=endpoint_id,
                    kind=EdgeKind.EXPOSES,
                    label=f"{func_name} exposes HTTP endpoint",
                ))

            elif trigger_type == "serviceBusQueue":
                queue_node_id = f"azure:servicebus:queue:{func_name}"
                result.nodes.append(GraphNode(
                    id=queue_node_id,
                    kind=NodeKind.QUEUE,
                    label=f"servicebus:{func_name}",
                    properties={"broker": "azure_servicebus", "queue": func_name},
                ))
                result.edges.append(GraphEdge(
                    source=queue_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"queue triggers {func_name}",
                ))

            elif trigger_type == "serviceBusTopic":
                topic_node_id = f"azure:servicebus:topic:{func_name}"
                result.nodes.append(GraphNode(
                    id=topic_node_id,
                    kind=NodeKind.TOPIC,
                    label=f"servicebus:{func_name}",
                    properties={"broker": "azure_servicebus", "topic": func_name},
                ))
                result.edges.append(GraphEdge(
                    source=topic_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"topic triggers {func_name}",
                ))

            elif trigger_type == "eventHub":
                hub_node_id = f"azure:eventhub:{func_name}"
                result.nodes.append(GraphNode(
                    id=hub_node_id,
                    kind=NodeKind.TOPIC,
                    label=f"eventhub:{func_name}",
                    properties={"broker": "azure_eventhub", "event_hub": func_name},
                ))
                result.edges.append(GraphEdge(
                    source=hub_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"event hub triggers {func_name}",
                ))

            elif trigger_type == "cosmosDB":
                resource_node_id = f"azure:cosmos:func:{func_name}"
                result.nodes.append(GraphNode(
                    id=resource_node_id,
                    kind=NodeKind.AZURE_RESOURCE,
                    label=f"cosmosdb:{func_name}",
                    properties={"cosmos_type": "trigger", "function_name": func_name},
                ))
                result.edges.append(GraphEdge(
                    source=resource_node_id,
                    target=func_node_id,
                    kind=EdgeKind.TRIGGERS,
                    label=f"CosmosDB triggers {func_name}",
                ))
