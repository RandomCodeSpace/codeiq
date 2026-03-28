"""IBM MQ detector for Java source files."""

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

# MQQueueManager instantiation with name
_QM_NEW_RE = re.compile(r'new\s+MQQueueManager\s*\(\s*"([^"]+)"')

# accessQueue("QUEUE_NAME", ...)
_ACCESS_QUEUE_RE = re.compile(r'accessQueue\s*\(\s*"([^"]+)"')

# MQQueue / MQTopic field or local variable declarations
_MQ_QUEUE_DECL_RE = re.compile(r'\bMQQueue\b')
_MQ_TOPIC_DECL_RE = re.compile(r'\bMQTopic\b')

# JMS-style IBM MQ connection factory
_MQ_JMS_FACTORY_RE = re.compile(r'\bMQConnectionFactory\b|\bMQQueueConnectionFactory\b|\bMQTopicConnectionFactory\b')

# createQueue / createTopic with IBM MQ JMS
_JMS_CREATE_QUEUE_RE = re.compile(r'createQueue\s*\(\s*"([^"]+)"')
_JMS_CREATE_TOPIC_RE = re.compile(r'createTopic\s*\(\s*"([^"]+)"')

# put / get calls indicate send / receive
_MQ_PUT_RE = re.compile(r'\bput\s*\(')
_MQ_GET_RE = re.compile(r'\bget\s*\(')


class IbmMqDetector:
    """Detects IBM MQ queue manager, queue, and topic usage in Java source files."""

    name: str = "ibm_mq"
    supported_languages: tuple[str, ...] = ("java",)

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")

        if "MQQueueManager" not in text and "JmsConnectionFactory" not in text and "com.ibm.mq" not in text and "MQQueue" not in text:
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
        seen_qms: set[str] = set()
        seen_queues: set[str] = set()
        seen_topics: set[str] = set()

        def _ensure_qm_node(qm_name: str) -> str:
            qm_id = f"ibmmq:qm:{qm_name}"
            if qm_name not in seen_qms:
                seen_qms.add(qm_name)
                result.nodes.append(GraphNode(
                    id=qm_id,
                    kind=NodeKind.MESSAGE_QUEUE,
                    label=f"ibmmq:qm:{qm_name}",
                    properties={"broker": "ibm_mq", "queue_manager": qm_name},
                ))
            return qm_id

        def _ensure_queue_node(queue_name: str) -> str:
            queue_id = f"ibmmq:queue:{queue_name}"
            if queue_name not in seen_queues:
                seen_queues.add(queue_name)
                result.nodes.append(GraphNode(
                    id=queue_id,
                    kind=NodeKind.QUEUE,
                    label=f"ibmmq:queue:{queue_name}",
                    properties={"broker": "ibm_mq", "queue": queue_name},
                ))
            return queue_id

        def _ensure_topic_node(topic_name: str) -> str:
            topic_id = f"ibmmq:topic:{topic_name}"
            if topic_name not in seen_topics:
                seen_topics.add(topic_name)
                result.nodes.append(GraphNode(
                    id=topic_id,
                    kind=NodeKind.TOPIC,
                    label=f"ibmmq:topic:{topic_name}",
                    properties={"broker": "ibm_mq", "topic": topic_name},
                ))
            return topic_id

        # Track whether we see put/get patterns for edge direction
        has_put = bool(_MQ_PUT_RE.search(text))
        has_get = bool(_MQ_GET_RE.search(text))

        # Detect MQQueueManager instantiation
        for i, line in enumerate(lines):
            m = _QM_NEW_RE.search(line)
            if m:
                qm_name = m.group(1)
                qm_id = _ensure_qm_node(qm_name)
                result.edges.append(GraphEdge(
                    source=class_node_id,
                    target=qm_id,
                    kind=EdgeKind.CONNECTS_TO,
                    label=f"{class_name} connects to queue manager {qm_name}",
                    properties={"queue_manager": qm_name},
                ))

        # Detect accessQueue calls
        for i, line in enumerate(lines):
            m = _ACCESS_QUEUE_RE.search(line)
            if m:
                queue_name = m.group(1)
                queue_id = _ensure_queue_node(queue_name)
                if has_put:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=queue_id,
                        kind=EdgeKind.SENDS_TO,
                        label=f"{class_name} sends to {queue_name}",
                        properties={"queue": queue_name},
                    ))
                if has_get:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=queue_id,
                        kind=EdgeKind.RECEIVES_FROM,
                        label=f"{class_name} receives from {queue_name}",
                        properties={"queue": queue_name},
                    ))
                if not has_put and not has_get:
                    result.edges.append(GraphEdge(
                        source=class_node_id,
                        target=queue_id,
                        kind=EdgeKind.CONNECTS_TO,
                        label=f"{class_name} accesses {queue_name}",
                        properties={"queue": queue_name},
                    ))

        # Detect JMS-style createQueue / createTopic
        for i, line in enumerate(lines):
            m = _JMS_CREATE_QUEUE_RE.search(line)
            if m:
                queue_name = m.group(1)
                _ensure_queue_node(queue_name)

            m = _JMS_CREATE_TOPIC_RE.search(line)
            if m:
                topic_name = m.group(1)
                _ensure_topic_node(topic_name)

        # If we found MQTopic declarations but no explicit topic names, create a
        # generic node to show MQ topic usage
        if _MQ_TOPIC_DECL_RE.search(text) and not seen_topics:
            result.nodes.append(GraphNode(
                id=f"ibmmq:topic:__unknown__",
                kind=NodeKind.TOPIC,
                label="ibmmq:topic:unknown",
                properties={"broker": "ibm_mq"},
            ))

        return result
