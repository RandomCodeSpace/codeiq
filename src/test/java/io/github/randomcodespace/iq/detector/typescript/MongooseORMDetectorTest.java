package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MongooseORMDetectorTest {

    private final MongooseORMDetector detector = new MongooseORMDetector();

    @Test
    void detectsMongooseUsage() {
        String code = """
                mongoose.connect('mongodb://localhost/test');
                const userSchema = new Schema({
                    name: String,
                    email: String
                });
                const User = mongoose.model('User', userSchema);
                User.find({});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // connection, schema entity, model entity
        assertTrue(result.nodes().size() >= 3);
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.DATABASE_CONNECTION));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.ENTITY));
        // Query edge
        assertFalse(result.edges().isEmpty());
    }

    @Test
    void noMatchOnNonMongooseCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "mongoose.connect('mongodb://localhost');\nconst s = new Schema({});";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
