"""Tests for KafkaJS detector (TypeScript/JavaScript)."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.typescript.kafka_js import KafkaJSDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "services/kafka-client.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestKafkaJSDetector:
    def setup_method(self):
        self.detector = KafkaJSDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "kafka_js"
        assert self.detector.supported_languages == ("typescript", "javascript")

    # --- Positive: Connection detection ---

    def test_detects_kafka_connection(self):
        source = """\
const { Kafka } = require('kafkajs');
const kafka = new Kafka({
  clientId: 'my-app',
  brokers: ['localhost:9092'],
});
"""
        result = self.detector.detect(_ctx(source))
        conn_nodes = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(conn_nodes) == 1
        assert conn_nodes[0].properties["library"] == "kafkajs"

    # --- Positive: Producer detection ---

    def test_detects_producer_and_send(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const producer = kafka.producer();
await producer.send({ topic: 'order-events', messages: [{ value: 'hello' }] });
"""
        result = self.detector.detect(_ctx(source))
        producer_nodes = [n for n in result.nodes if n.properties.get("role") == "producer"]
        assert len(producer_nodes) >= 1
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC and n.properties.get("topic")]
        assert any(t.properties["topic"] == "order-events" for t in topics)
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1
        assert produce_edges[0].properties["topic"] == "order-events"

    def test_detects_producer_send_single_quotes(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const producer = kafka.producer();
await producer.send({ topic: 'metrics-data', messages: [] });
"""
        result = self.detector.detect(_ctx(source))
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1
        assert produce_edges[0].properties["topic"] == "metrics-data"

    # --- Positive: Consumer detection ---

    def test_detects_consumer_with_group_id(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const consumer = kafka.consumer({ groupId: 'order-group' });
await consumer.subscribe({ topic: 'order-events' });
"""
        result = self.detector.detect(_ctx(source))
        consumer_nodes = [n for n in result.nodes if n.properties.get("group_id") == "order-group"]
        assert len(consumer_nodes) == 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1
        assert consume_edges[0].properties["topic"] == "order-events"

    def test_detects_consumer_subscribe(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const consumer = kafka.consumer({ groupId: 'my-group' });
await consumer.subscribe({ topic: 'notifications' });
"""
        result = self.detector.detect(_ctx(source))
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1
        assert consume_edges[0].properties["topic"] == "notifications"

    # --- Positive: Event handler detection ---

    def test_detects_each_message_handler(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const consumer = kafka.consumer({ groupId: 'handler-group' });
await consumer.run({ eachMessage: async ({ topic, partition, message }) => {
  console.log(message.value.toString());
}});
"""
        result = self.detector.detect(_ctx(source))
        event_nodes = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(event_nodes) == 1
        assert event_nodes[0].properties["handler"] == "eachMessage"

    # --- Positive: Full pipeline ---

    def test_detects_full_pipeline(self):
        source = """\
import { Kafka } from 'kafkajs';

const kafka = new Kafka({
  clientId: 'my-app',
  brokers: ['localhost:9092'],
});

const producer = kafka.producer();
await producer.send({ topic: 'raw-events', messages: [{ value: 'data' }] });

const consumer = kafka.consumer({ groupId: 'processor-group' });
await consumer.subscribe({ topic: 'raw-events' });
await consumer.run({ eachMessage: async ({ message }) => {
  process(message);
}});
"""
        result = self.detector.detect(_ctx(source))
        conn_nodes = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(conn_nodes) == 1
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) >= 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) >= 1
        event_nodes = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(event_nodes) == 1

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx(""))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_kafka_keywords(self):
        source = """\
import express from 'express';
const app = express();
app.get('/health', (req, res) => res.send('ok'));
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_kafka_new(self):
        source = """\
const client = new MongoClient('mongodb://localhost');
"""
        result = self.detector.detect(_ctx(source))
        conn_nodes = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(conn_nodes) == 0

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
const kafka = new Kafka({ brokers: ['localhost:9092'] });
const producer = kafka.producer();
await producer.send({ topic: 'events', messages: [] });
const consumer = kafka.consumer({ groupId: 'grp' });
await consumer.subscribe({ topic: 'events' });
await consumer.run({ eachMessage: async (msg) => {} });
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.source for e in r1.edges] == [e.source for e in r2.edges]
        assert [e.target for e in r1.edges] == [e.target for e in r2.edges]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
