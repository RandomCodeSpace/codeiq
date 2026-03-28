"""Tests for Kafka Python detector (confluent-kafka, aiokafka, kafka-python)."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.python.kafka_python import KafkaPythonDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "services/producer.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestKafkaPythonDetector:
    def setup_method(self):
        self.detector = KafkaPythonDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "kafka_python"
        assert self.detector.supported_languages == ("python",)

    # --- Positive: Producer detection ---

    def test_detects_kafka_producer(self):
        source = """\
from kafka import KafkaProducer

producer = KafkaProducer(bootstrap_servers='localhost:9092')
producer.send('order-events', b'hello')
"""
        result = self.detector.detect(_ctx(source))
        producer_nodes = [n for n in result.nodes if n.properties.get("role") == "producer"]
        assert len(producer_nodes) >= 1
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1
        assert produce_edges[0].properties["topic"] == "order-events"

    def test_detects_confluent_producer(self):
        source = """\
from confluent_kafka import Producer

p = Producer({'bootstrap.servers': 'localhost:9092'})
p.produce('user-signups', value=b'data')
"""
        result = self.detector.detect(_ctx(source))
        producer_nodes = [n for n in result.nodes if n.properties.get("role") == "producer"]
        assert len(producer_nodes) >= 1
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1
        assert produce_edges[0].properties["topic"] == "user-signups"

    def test_detects_aiokafka_producer(self):
        source = """\
from aiokafka import AIOKafkaProducer

producer = AIOKafkaProducer(bootstrap_servers='localhost:9092')
await producer.send('async-events', b'msg')
"""
        result = self.detector.detect(_ctx(source))
        producer_nodes = [n for n in result.nodes if n.properties.get("role") == "producer"]
        assert len(producer_nodes) >= 1
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1
        assert produce_edges[0].properties["topic"] == "async-events"

    # --- Positive: Consumer detection ---

    def test_detects_kafka_consumer(self):
        source = """\
from kafka import KafkaConsumer

consumer = KafkaConsumer('initial-topic', bootstrap_servers='localhost:9092')
consumer.subscribe(['order-events'])
"""
        result = self.detector.detect(_ctx(source))
        consumer_nodes = [n for n in result.nodes if n.properties.get("role") == "consumer"]
        assert len(consumer_nodes) >= 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1
        assert consume_edges[0].properties["topic"] == "order-events"

    def test_detects_confluent_consumer(self):
        source = """\
from confluent_kafka import Consumer

c = Consumer({'bootstrap.servers': 'localhost:9092', 'group.id': 'my-group'})
c.subscribe(['payment-events'])
"""
        result = self.detector.detect(_ctx(source))
        consumer_nodes = [n for n in result.nodes if n.properties.get("role") == "consumer"]
        assert len(consumer_nodes) >= 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1
        assert consume_edges[0].properties["topic"] == "payment-events"

    def test_detects_aiokafka_consumer(self):
        source = """\
from aiokafka import AIOKafkaConsumer

consumer = AIOKafkaConsumer(bootstrap_servers='localhost:9092')
consumer.subscribe(['stream-data'])
"""
        result = self.detector.detect(_ctx(source))
        consumer_nodes = [n for n in result.nodes if n.properties.get("role") == "consumer"]
        assert len(consumer_nodes) >= 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1

    # --- Positive: Import detection ---

    def test_detects_kafka_import(self):
        source = """\
from kafka import KafkaProducer

producer = KafkaProducer(bootstrap_servers='localhost:9092')
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1
        assert import_edges[0].properties["library"] == "kafka"

    def test_detects_confluent_kafka_import(self):
        source = """\
from confluent_kafka import Producer

p = Producer({'bootstrap.servers': 'localhost'})
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1
        assert import_edges[0].properties["library"] == "confluent_kafka"

    def test_detects_aiokafka_import(self):
        source = """\
import aiokafka

producer = aiokafka.AIOKafkaProducer()
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1
        assert import_edges[0].properties["library"] == "aiokafka"

    # --- Positive: Combined producer + consumer ---

    def test_detects_producer_and_consumer(self):
        source = """\
from kafka import KafkaProducer, KafkaConsumer

producer = KafkaProducer(bootstrap_servers='localhost:9092')
consumer = KafkaConsumer(bootstrap_servers='localhost:9092')

producer.send('outgoing-events', b'data')
consumer.subscribe(['incoming-events'])
"""
        result = self.detector.detect(_ctx(source))
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC]
        assert len(topics) >= 2
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(produce_edges) == 1
        assert len(consume_edges) == 1

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx(""))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_kafka_keywords(self):
        source = """\
import requests

def get_data():
    return requests.get('http://example.com')
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_kafka_send(self):
        source = """\
class EmailService:
    def send_email(self):
        self.mailer.send('user@example.com', 'subject', 'body')
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
from kafka import KafkaProducer, KafkaConsumer

producer = KafkaProducer(bootstrap_servers='localhost:9092')
consumer = KafkaConsumer(bootstrap_servers='localhost:9092')
producer.send('events', b'data')
consumer.subscribe(['events'])
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
