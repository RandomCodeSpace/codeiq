"""TIBCO EMS detector for Java source files."""

from __future__ import annotations

import re

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.utils import decode_text
from osscodeiq.models.graph import (
    EdgeKind,
    GraphEdge,
    GraphNode,
    NodeKind,
    SourceLocation,
)

_CLASS_RE = re.compile(r"(?:public\s+)?class\s+(\w+)")

# TIBCO EMS connection factories
_TIBJMS_FACTORY_RE = re.compile(
    r'\b(TibjmsConnectionFactory|TibjmsQueueConnectionFactory|TibjmsTopicConnectionFactory)\b'
)

# Server URL patterns (e.g. "tcp://ems-server:7222")
_SERVER_URL_RE = re.compile(r'"(tcp://[^"]+)"')

# createQueue / createTopic
_CREATE_QUEUE_RE = re.compile(r'createQueue\s*\(\s*"([^"]+)"')
_CREATE_TOPIC_RE = re.compile(r'createTopic\s*\(\s*"([^"]+)"')

# send / publish patterns
_SEND_RE = re.compile(r'\bsend\s*\(')
_PUBLISH_RE = re.compile(r'\bpublish\s*\(')

# receive / onMessage patterns
_RECEIVE_RE = re.compile(r'\breceive\s*\(')
_ON_MESSAGE_RE = re.compile(r'\bonMessage\s*\(')

# MessageProducer / MessageConsumer declarations
_PRODUCER_RE = re.compile(r'\bMessageProducer\b')
_CONSUMER_RE = re.compile(r'\bMessageConsumer\b')

# Tibjms-specific queue/topic classes
_TIBJMS_QUEUE_RE = re.compile(r'new\s+TibjmsQueue\s*\(\s*"([^"]+)"')
_TIBJMS_TOPIC_RE = re.compile(r'new\s+TibjmsTopic\s*\(\s*"([^"]+)"')


class TibcoEmsDetector:
    """Detects TIBCO EMS queue and topic usage in Java source files."""

    name: str = "tibco_ems"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        if "tibjms" not in text and "TibjmsConnectionFactory" not in text and "com.tibco" not in text and "TIBJMS" not in text:
            return result

        # Find class name
        class_name: str | None = None
        for line in lines:
            cm = _CLASS_RE.search(line)
            if cm:
                class_name = cm.group(1)
                break

        if not class_name:
            return result

        class_node_id = f"{ctx.file_path}:{class_name}"
        seen_queues: set[str] = set()
        seen_topics: set[str] = set()

        def _ensure_queue_node(queue_name: str) -> str:
            queue_id = f"ems:queue:{queue_name}"
            if queue_name not in seen_queues:
                seen_queues.add(queue_name)
                result.nodes.append(GraphNode(
                    id=queue_id,
                    kind=NodeKind.QUEUE,
                    label=f"ems:queue:{queue_name}",
                    properties={"broker": "tibco_ems", "queue": queue_name},
                ))
            return queue_id

        def _ensure_topic_node(topic_name: str) -> str:
            topic_id = f"ems:topic:{topic_name}"
            if topic_name not in seen_topics:
                seen_topics.add(topic_name)
                result.nodes.append(GraphNode(
                    id=topic_id,
                    kind=NodeKind.TOPIC,
                    label=f"ems:topic:{topic_name}",
                    properties={"broker": "tibco_ems", "topic": topic_name},
                ))
            return topic_id

        # Detect whether this class is a producer or consumer
        is_producer = bool(_SEND_RE.search(text) or _PUBLISH_RE.search(text) or _PRODUCER_RE.search(text))
        is_consumer = bool(_RECEIVE_RE.search(text) or _ON_MESSAGE_RE.search(text) or _CONSUMER_RE.search(text))

        # Detect connection factory — create a node for the EMS server
        server_urls: list[str] = []
        for i, line in enumerate(lines):
            m = _TIBJMS_FACTORY_RE.search(line)
            if m:
                factory_type = m.group(1)
                # Look for server URL on same line or next few lines
                for j in range(max(0, i - 1), min(len(lines), i + 4)):
                    url_m = _SERVER_URL_RE.search(lines[j])
                    if url_m:
                        server_urls.append(url_m.group(1))

                # Create an EMS connection node
                node_id = f"ems:server:{factory_type}"
                result.nodes.append(GraphNode(
                    id=node_id,
                    kind=NodeKind.MESSAGE_QUEUE,
                    label=f"ems:{factory_type}",
                    properties={
                        "broker": "tibco_ems",
                        "factory_type": factory_type,
                        **({"server_url": server_urls[0]} if server_urls else {}),
                    },
                ))
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=node_id,
                    kind=EdgeKind.CONNECTS_TO,
                    label=f"{class_name} connects to EMS via {factory_type}",
                    properties={"factory_type": factory_type},
                ))

        # Detect createQueue / createTopic
        for i, line in enumerate(lines):
            m = _CREATE_QUEUE_RE.search(line)
            if m:
                queue_name = m.group(1)
                queue_id = _ensure_queue_node(queue_name)
                if is_producer:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=queue_id,
                        kind=EdgeKind.SENDS_TO,
                        label=f"{class_name} sends to {queue_name}",
                        properties={"queue": queue_name},
                    ))
                if is_consumer:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=queue_id,
                        kind=EdgeKind.RECEIVES_FROM,
                        label=f"{class_name} receives from {queue_name}",
                        properties={"queue": queue_name},
                    ))

            m = _CREATE_TOPIC_RE.search(line)
            if m:
                topic_name = m.group(1)
                topic_id = _ensure_topic_node(topic_name)
                if is_producer:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=topic_id,
                        kind=EdgeKind.SENDS_TO,
                        label=f"{class_name} sends to {topic_name}",
                        properties={"topic": topic_name},
                    ))
                if is_consumer:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=topic_id,
                        kind=EdgeKind.RECEIVES_FROM,
                        label=f"{class_name} receives from {topic_name}",
                        properties={"topic": topic_name},
                    ))

        # Detect TibjmsQueue / TibjmsTopic direct instantiation
        for i, line in enumerate(lines):
            m = _TIBJMS_QUEUE_RE.search(line)
            if m:
                queue_name = m.group(1)
                _ensure_queue_node(queue_name)

            m = _TIBJMS_TOPIC_RE.search(line)
            if m:
                topic_name = m.group(1)
                _ensure_topic_node(topic_name)

        return result
