package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class KafkaJSDetectorTest {

    private final KafkaJSDetector detector = new KafkaJSDetector();

    @Test
    void detectsKafkaUsage() {
        String code = """
                const kafka = new Kafka({
                    clientId: 'my-app',
                    brokers: ['localhost:9092']
                });
                const producer = kafka.producer();
                producer.send({ topic: 'user-events', messages: [] });
                const consumer = kafka.consumer({ groupId: 'my-group' });
                consumer.subscribe({ topic: 'user-events' });
                consumer.run({ eachMessage: async ({ topic, partition, message }) => {} });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/kafka.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // Connection, producer, topic, consumer, event nodes
        assertTrue(result.nodes().size() >= 4);
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.DATABASE_CONNECTION));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.TOPIC));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.EVENT));
        // Edges for produces and consumes
        assertTrue(result.edges().size() >= 2);
    }

    @Test
    void detectsKafkaConnectionNode() {
        String code = """
                const kafka = new Kafka({
                    brokers: ['kafka:9092']
                });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/kafka.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var conn = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.DATABASE_CONNECTION)
                .findFirst();
        assertTrue(conn.isPresent());
        assertEquals("kafka", conn.get().getProperties().get("broker"));
        assertEquals("kafkajs", conn.get().getProperties().get("library"));
    }

    @Test
    void detectsProducerSendWithTopicNode() {
        String code = """
                const kafka = new Kafka({ brokers: [] });
                const producer = kafka.producer();
                await producer.send({ topic: 'order-placed', messages: [{ value: 'data' }] });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/producer.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.TOPIC
                && n.getLabel().contains("order-placed"));
        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.PRODUCES);
    }

    @Test
    void detectsConsumerWithGroupIdAndSubscribe() {
        String code = """
                const kafka = new Kafka({ brokers: [] });
                const consumer = kafka.consumer({ groupId: 'payments-service' });
                await consumer.subscribe({ topic: 'order-placed' });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/consumer.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var consumerNode = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.TOPIC && "consumer".equals(n.getProperties().get("role")))
                .findFirst();
        assertTrue(consumerNode.isPresent());
        assertEquals("payments-service", consumerNode.get().getProperties().get("group_id"));

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.CONSUMES);
    }

    @Test
    void detectsEachMessageHandler() {
        String code = """
                const kafka = new Kafka({ brokers: [] });
                await consumer.run({ eachMessage: async ({ message }) => {
                    console.log(message.value);
                }});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/consumer.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.EVENT
                && "kafka:eachMessage".equals(n.getLabel()));
    }

    @Test
    void topicDeduplicationForSameTopicProducedAndConsumed() {
        // Same topic referenced by send and subscribe should only produce one TOPIC node
        String code = """
                const kafka = new Kafka({ brokers: [] });
                producer.send({ topic: 'events', messages: [] });
                consumer.subscribe({ topic: 'events' });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/events.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long topicCount = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.TOPIC && n.getLabel().contains("events"))
                .count();
        assertEquals(1, topicCount, "Same topic should not be deduplicated into multiple nodes");
    }

    @Test
    void noMatchWithoutKafka() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = """
                const kafka = new Kafka({ brokers: [] });
                const producer = kafka.producer();
                producer.send({ topic: 'events', messages: [] });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("kafka_js", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
