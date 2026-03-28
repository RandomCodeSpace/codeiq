"""Azure Service Bus and Event Hub detector for Java and TypeScript source files."""

from __future__ import annotations

import re

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.utils import decode_text
from code_intelligence.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")

# TypeScript/JavaScript class or const pattern
_TS_CLASS_RE = re.compile(r"(?:export\s+)?class\s+(\w+)")
_TS_CONST_RE = re.compile(r"(?:export\s+)?(?:const|let|var)\s+(\w+)")

# --- Java Azure Service Bus SDK ---
_SB_SENDER_CLIENT_RE = re.compile(r'\bServiceBusSenderClient\b')
_SB_RECEIVER_CLIENT_RE = re.compile(r'\bServiceBusReceiverClient\b')
_SB_PROCESSOR_CLIENT_RE = re.compile(r'\bServiceBusProcessorClient\b')
_SB_CLIENT_RE = re.compile(r'\bServiceBusClient\b')
_SB_CLIENT_BUILDER_RE = re.compile(r'\bServiceBusClientBuilder\b')

# --- JS/TS Azure Service Bus SDK ---
_SB_SENDER_JS_RE = re.compile(r'\bServiceBusSender\b')
_SB_RECEIVER_JS_RE = re.compile(r'\bServiceBusReceiver\b')

# --- Azure Event Hub (both Java and JS/TS) ---
_EH_PRODUCER_RE = re.compile(r'\bEventHubProducerClient\b')
_EH_CONSUMER_RE = re.compile(r'\bEventHubConsumerClient\b')
_EH_PROCESSOR_RE = re.compile(r'\bEventProcessorClient\b')

# --- Azure Functions trigger annotations ---
_SB_QUEUE_TRIGGER_RE = re.compile(r'@ServiceBusQueueTrigger\s*\([^)]*name\s*=\s*"([^"]*)"')
_SB_TOPIC_TRIGGER_RE = re.compile(r'@ServiceBusTopicTrigger\s*\([^)]*name\s*=\s*"([^"]*)"')
_EH_TRIGGER_RE = re.compile(r'@EventHubTrigger\s*\([^)]*name\s*=\s*"([^"]*)"')

# Generic trigger annotations (looser match)
_SB_QUEUE_TRIGGER_LOOSE_RE = re.compile(r'@ServiceBusQueueTrigger')
_SB_TOPIC_TRIGGER_LOOSE_RE = re.compile(r'@ServiceBusTopicTrigger')
_EH_TRIGGER_LOOSE_RE = re.compile(r'@EventHubTrigger')

# Queue/topic name extraction from builder patterns
_QUEUE_NAME_RE = re.compile(r'(?:queueName|queue)\s*\(\s*"([^"]+)"')
_TOPIC_NAME_RE = re.compile(r'(?:topicName|topic)\s*\(\s*"([^"]+)"')
_EH_NAME_RE = re.compile(r'(?:eventHubName|eventHub)\s*\(\s*"([^"]+)"')

# String literal near ServiceBus/EventHub patterns
_STRING_LITERAL_RE = re.compile(r'"([^"]+)"')

# JS/TS createSender/createReceiver with queue/topic name
_JS_CREATE_SENDER_RE = re.compile(r'createSender\s*\(\s*"([^"]+)"')
_JS_CREATE_RECEIVER_RE = re.compile(r'createReceiver\s*\(\s*"([^"]+)"')

# JS/TS Service Bus subscription
_JS_SUBSCRIBE_RE = re.compile(r'subscribe\s*\(')

# JS/TS EventHub
_JS_EH_SEND_RE = re.compile(r'sendBatch\s*\(')


class AzureMessagingDetector:
    """Detects Azure Service Bus and Event Hub usage in Java and TypeScript source files."""

    name: str = "azure_messaging"
    supported_languages: tuple[str, ...] = ("java", "typescript", "javascript")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        if "ServiceBus" not in text and "EventHub" not in text and "azure-messaging" not in text and "@azure/service-bus" not in text and "@azure/event-hubs" not in text:
            return result

        # Find class/module name depending on language
        class_name: str | None = None
        if ctx.language in ("typescript", "javascript"):
            for line in lines:
                cm = _TS_CLASS_RE.search(line)
                if cm:
                    class_name = cm.group(1)
                    break
            if not class_name:
                # Fall back to file name
                class_name = ctx.file_path.rsplit("/", 1)[-1].rsplit(".", 1)[0]
        else:
            for line in lines:
                cm = _CLASS_RE.search(line)
                if cm:
                    class_name = cm.group(1)
                    break

        if not class_name:
            return result

        class_node_id = f"{ctx.file_path}:{class_name}"
        seen_sb_queues: set[str] = set()
        seen_sb_topics: set[str] = set()
        seen_event_hubs: set[str] = set()

        def _ensure_sb_queue_node(name: str) -> str:
            node_id = f"azure:servicebus:{name}"
            if name not in seen_sb_queues:
                seen_sb_queues.add(name)
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.QUEUE,
                    label=f"azure:servicebus:{name}",
                    properties={"broker": "azure_servicebus", "queue": name},
                ))
            return node_id

        def _ensure_sb_topic_node(name: str) -> str:
            node_id = f"azure:servicebus:{name}"
            if name not in seen_sb_topics:
                seen_sb_topics.add(name)
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.TOPIC,
                    label=f"azure:servicebus:{name}",
                    properties={"broker": "azure_servicebus", "topic": name},
                ))
            return node_id

        def _ensure_eventhub_node(name: str) -> str:
            node_id = f"azure:eventhub:{name}"
            if name not in seen_event_hubs:
                seen_event_hubs.add(name)
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.TOPIC,
                    label=f"azure:eventhub:{name}",
                    properties={"broker": "azure_eventhub", "event_hub": name},
                ))
            return node_id

        # Determine producer/consumer role from class patterns
        is_sb_sender = bool(
            _SB_SENDER_CLIENT_RE.search(text)
            or _SB_SENDER_JS_RE.search(text)
        )
        is_sb_receiver = bool(
            _SB_RECEIVER_CLIENT_RE.search(text)
            or _SB_PROCESSOR_CLIENT_RE.search(text)
            or _SB_RECEIVER_JS_RE.search(text)
        )
        is_eh_producer = bool(_EH_PRODUCER_RE.search(text))
        is_eh_consumer = bool(
            _EH_CONSUMER_RE.search(text)
            or _EH_PROCESSOR_RE.search(text)
        )
        has_sb_client = bool(_SB_CLIENT_RE.search(text) or _SB_CLIENT_BUILDER_RE.search(text))

        # Extract queue names from builder patterns
        queue_names: list[str] = []
        topic_names: list[str] = []
        eh_names: list[str] = []

        for line in lines:
            m = _QUEUE_NAME_RE.search(line)
            if m:
                queue_names.append(m.group(1))

            m = _TOPIC_NAME_RE.search(line)
            if m:
                topic_names.append(m.group(1))

            m = _EH_NAME_RE.search(line)
            if m:
                eh_names.append(m.group(1))

        # JS/TS createSender / createReceiver with queue/topic name
        for line in lines:
            m = _JS_CREATE_SENDER_RE.search(line)
            if m:
                queue_names.append(m.group(1))
                is_sb_sender = True

            m = _JS_CREATE_RECEIVER_RE.search(line)
            if m:
                queue_names.append(m.group(1))
                is_sb_receiver = True

        # Azure Functions trigger annotations
        for line in lines:
            m = _SB_QUEUE_TRIGGER_RE.search(line)
            if m:
                queue_names.append(m.group(1))
                is_sb_receiver = True

            m = _SB_TOPIC_TRIGGER_RE.search(line)
            if m:
                topic_names.append(m.group(1))
                is_sb_receiver = True

            m = _EH_TRIGGER_RE.search(line)
            if m:
                eh_names.append(m.group(1))
                is_eh_consumer = True

            # Loose trigger detection (without name extraction)
            if _SB_QUEUE_TRIGGER_LOOSE_RE.search(line) and not _SB_QUEUE_TRIGGER_RE.search(line):
                is_sb_receiver = True
            if _SB_TOPIC_TRIGGER_LOOSE_RE.search(line) and not _SB_TOPIC_TRIGGER_RE.search(line):
                is_sb_receiver = True
            if _EH_TRIGGER_LOOSE_RE.search(line) and not _EH_TRIGGER_RE.search(line):
                is_eh_consumer = True

        # Create Service Bus queue nodes and edges
        for qname in queue_names:
            queue_id = _ensure_sb_queue_node(qname)
            if is_sb_sender:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=queue_id,
                    kind=EdgeKind.SENDS_TO,
                    label=f"{class_name} sends to {qname}",
                    properties={"queue": qname},
                ))
            if is_sb_receiver:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=queue_id,
                    kind=EdgeKind.RECEIVES_FROM,
                    label=f"{class_name} receives from {qname}",
                    properties={"queue": qname},
                ))

        # Create Service Bus topic nodes and edges
        for tname in topic_names:
            topic_id = _ensure_sb_topic_node(tname)
            if is_sb_sender:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=topic_id,
                    kind=EdgeKind.SENDS_TO,
                    label=f"{class_name} sends to {tname}",
                    properties={"topic": tname},
                ))
            if is_sb_receiver:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=topic_id,
                    kind=EdgeKind.RECEIVES_FROM,
                    label=f"{class_name} receives from {tname}",
                    properties={"topic": tname},
                ))

        # Create Event Hub nodes and edges
        for ehname in eh_names:
            eh_id = _ensure_eventhub_node(ehname)
            if is_eh_producer:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=eh_id,
                    kind=EdgeKind.SENDS_TO,
                    label=f"{class_name} sends to {ehname}",
                    properties={"event_hub": ehname},
                ))
            if is_eh_consumer:
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=eh_id,
                    kind=EdgeKind.RECEIVES_FROM,
                    label=f"{class_name} receives from {ehname}",
                    properties={"event_hub": ehname},
                ))

        # If we detected SDK usage but no explicit names, create generic nodes
        # to at least show the dependency
        if is_sb_sender and not queue_names and not topic_names:
            result.nodes.append(GraphNode(
                id="azure:servicebus:__sender__",
                kind=NodeKind.QUEUE,
                label="azure:servicebus:sender",
                properties={"broker": "azure_servicebus", "role": "sender"},
            ))
            result.edges.append(GraphEdge(
                source=class_node_id,
                target="azure:servicebus:__sender__",
                kind=EdgeKind.SENDS_TO,
                label=f"{class_name} sends to Azure Service Bus",
                properties={},
            ))
        elif is_sb_receiver and not queue_names and not topic_names:
            result.nodes.append(GraphNode(
                id="azure:servicebus:__receiver__",
                kind=NodeKind.QUEUE,
                label="azure:servicebus:receiver",
                properties={"broker": "azure_servicebus", "role": "receiver"},
            ))
            result.edges.append(GraphEdge(
                source=class_node_id,
                target="azure:servicebus:__receiver__",
                kind=EdgeKind.RECEIVES_FROM,
                label=f"{class_name} receives from Azure Service Bus",
                properties={},
            ))
        elif has_sb_client and not queue_names and not topic_names and not is_sb_sender and not is_sb_receiver:
            result.nodes.append(GraphNode(
                id="azure:servicebus:__client__",
                kind=NodeKind.QUEUE,
                label="azure:servicebus:client",
                properties={"broker": "azure_servicebus", "role": "client"},
            ))
            result.edges.append(GraphEdge(
                source=class_node_id,
                target="azure:servicebus:__client__",
                kind=EdgeKind.CONNECTS_TO,
                label=f"{class_name} connects to Azure Service Bus",
                properties={},
            ))

        if is_eh_producer and not eh_names:
            result.nodes.append(GraphNode(
                id="azure:eventhub:__producer__",
                kind=NodeKind.TOPIC,
                label="azure:eventhub:producer",
                properties={"broker": "azure_eventhub", "role": "producer"},
            ))
            result.edges.append(GraphEdge(
                source=class_node_id,
                target="azure:eventhub:__producer__",
                kind=EdgeKind.SENDS_TO,
                label=f"{class_name} sends to Azure Event Hub",
                properties={},
            ))
        elif is_eh_consumer and not eh_names:
            result.nodes.append(GraphNode(
                id="azure:eventhub:__consumer__",
                kind=NodeKind.TOPIC,
                label="azure:eventhub:consumer",
                properties={"broker": "azure_eventhub", "role": "consumer"},
            ))
            result.edges.append(GraphEdge(
                source=class_node_id,
                target="azure:eventhub:__consumer__",
                kind=EdgeKind.RECEIVES_FROM,
                label=f"{class_name} receives from Azure Event Hub",
                properties={},
            ))

        return result
