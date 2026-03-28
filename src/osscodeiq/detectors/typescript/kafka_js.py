"""KafkaJS detector for TypeScript/JavaScript source files.

Detects usage of the KafkaJS library in Node.js applications.
"""

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

# Connection pattern: new Kafka({
_KAFKA_NEW_RE = re.compile(r"new\s+Kafka\s*\(\s*\{", re.MULTILINE)

# Producer pattern: kafka.producer()
_PRODUCER_RE = re.compile(r"\.producer\s*\(\s*\)", re.MULTILINE)

# Producer send: producer.send({ topic: 'name' })
_PRODUCER_SEND_RE = re.compile(
    r"\.send\s*\(\s*\{\s*topic\s*:\s*['\"]([^'\"]+)['\"]", re.MULTILINE
)

# Consumer pattern: kafka.consumer({ groupId: 'group' })
_CONSUMER_RE = re.compile(
    r"\.consumer\s*\(\s*\{\s*groupId\s*:\s*['\"]([^'\"]+)['\"]", re.MULTILINE
)

# Consumer subscribe: consumer.subscribe({ topic: 'name' })
_SUBSCRIBE_RE = re.compile(
    r"\.subscribe\s*\(\s*\{\s*topic\s*:\s*['\"]([^'\"]+)['\"]", re.MULTILINE
)

# Consumer run: consumer.run({ eachMessage:
_RUN_EACH_RE = re.compile(
    r"\.run\s*\(\s*\{\s*eachMessage\s*:", re.MULTILINE
)


class KafkaJSDetector:
    """Detects KafkaJS usage in TypeScript/JavaScript applications."""

    name: str = "kafka_js"
    supported_languages: tuple[str, ...] = ("typescript", "javascript")

    def detect(self, ctx: DetectorContext) -> DetectorResult:
        result = DetectorResult()
        text = decode_text(ctx)
        lines = text.split("\n")
        fp = ctx.file_path

        # Quick bail-out
        if "Kafka" not in text and "kafka" not in text:
            return result

        seen_topics: set[str] = set()
        file_node_id = f"kafka_js:{fp}"

        def _ensure_topic(topic: str, line: int) -> str:
            topic_id = f"kafka_js:{fp}:topic:{topic}"
            if topic not in seen_topics:
                seen_topics.add(topic)
                result.nodes.append(GraphNode(
                    id=topic_id,
                    kind=NodeKind.TOPIC,
                    label=f"kafka:{topic}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=line),
                    properties={"broker": "kafka", "topic": topic},
                ))
            return topic_id

        # Detect new Kafka({ -> DATABASE_CONNECTION node
        for i, line in enumerate(lines):
            lineno = i + 1
            if _KAFKA_NEW_RE.search(line):
                result.nodes.append(GraphNode(
                    id=f"kafka_js:{fp}:connection:{lineno}",
                    kind=NodeKind.DATABASE_CONNECTION,
                    label="KafkaJS connection",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=lineno),
                    properties={"broker": "kafka", "library": "kafkajs"},
                ))

        # Detect kafka.producer() -> properties on file node
        for i, line in enumerate(lines):
            lineno = i + 1
            if _PRODUCER_RE.search(line):
                result.nodes.append(GraphNode(
                    id=f"kafka_js:{fp}:producer:{lineno}",
                    kind=NodeKind.TOPIC,
                    label="kafka:producer",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=lineno),
                    properties={"role": "producer"},
                ))

        # Detect producer.send({ topic: 'name' }) -> TOPIC + PRODUCES edge
        for i, line in enumerate(lines):
            lineno = i + 1
            m = _PRODUCER_SEND_RE.search(line)
            if m:
                topic = m.group(1)
                topic_id = _ensure_topic(topic, lineno)
                result.edges.append(GraphEdge(
                    source=file_node_id,
                    target=topic_id,
                    kind=EdgeKind.PRODUCES,
                    label=f"produces to {topic}",
                    properties={"topic": topic},
                ))

        # Detect kafka.consumer({ groupId: 'group' }) -> properties
        for i, line in enumerate(lines):
            lineno = i + 1
            m = _CONSUMER_RE.search(line)
            if m:
                group_id = m.group(1)
                result.nodes.append(GraphNode(
                    id=f"kafka_js:{fp}:consumer:{lineno}",
                    kind=NodeKind.TOPIC,
                    label=f"kafka:consumer:{group_id}",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=lineno),
                    properties={"role": "consumer", "group_id": group_id},
                ))

        # Detect consumer.subscribe({ topic: 'name' }) -> TOPIC + CONSUMES edge
        for i, line in enumerate(lines):
            lineno = i + 1
            m = _SUBSCRIBE_RE.search(line)
            if m:
                topic = m.group(1)
                topic_id = _ensure_topic(topic, lineno)
                result.edges.append(GraphEdge(
                    source=file_node_id,
                    target=topic_id,
                    kind=EdgeKind.CONSUMES,
                    label=f"consumes from {topic}",
                    properties={"topic": topic},
                ))

        # Detect consumer.run({ eachMessage: }) -> EVENT node
        for i, line in enumerate(lines):
            lineno = i + 1
            if _RUN_EACH_RE.search(line):
                result.nodes.append(GraphNode(
                    id=f"kafka_js:{fp}:event:{lineno}",
                    kind=NodeKind.EVENT,
                    label="kafka:eachMessage",
                    module=ctx.module_name,
                    location=SourceLocation(file_path=fp, line_start=lineno),
                    properties={"handler": "eachMessage"},
                ))

        return result
