"""Tests for Kafka producer/consumer detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.java.kafka import KafkaDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "OrderEventHandler.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestKafkaDetector:
    def setup_method(self):
        self.detector = KafkaDetector()

    def test_detects_kafka_listener(self):
        source = """\
public class OrderConsumer {

    @KafkaListener(topics = "order-events", groupId = "order-group")
    public void handleOrderEvent(String message) {
        processOrder(message);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC]
        assert len(topics) == 1
        assert topics[0].properties["topic"] == "order-events"
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1
        assert consume_edges[0].properties.get("group_id") == "order-group"

    def test_detects_kafka_template_send(self):
        source = """\
public class OrderPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publishOrder(Order order) {
        kafkaTemplate.send("order-events", order.toJson());
    }
}
"""
        result = self.detector.detect(_ctx(source))
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC]
        assert len(topics) == 1
        assert topics[0].properties["topic"] == "order-events"
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) == 1

    def test_detects_both_consumer_and_producer(self):
        source = """\
public class OrderProcessor {

    @KafkaListener(topics = "raw-orders")
    public void consume(String msg) {
        String processed = transform(msg);
        kafkaTemplate.send("processed-orders", processed);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC]
        assert len(topics) == 2
        topic_names = {t.properties["topic"] for t in topics}
        assert "raw-orders" in topic_names
        assert "processed-orders" in topic_names
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(consume_edges) >= 1
        assert len(produce_edges) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class PlainService { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_kafka_keywords(self):
        source = """\
public class UserService {
    public void sendEmail(String to) {
        emailService.send(to, "subject", "body");
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
public class EventHandler {
    @KafkaListener(topics = "events", groupId = "grp")
    public void handle(String msg) {}

    public void emit() {
        kafkaTemplate.send("results", "ok");
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
