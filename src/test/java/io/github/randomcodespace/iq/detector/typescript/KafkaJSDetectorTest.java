package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

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
    void noMatchWithoutKafka() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "const kafka = new Kafka({\n  brokers: []\n});";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
