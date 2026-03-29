package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SequelizeORMDetectorTest {

    private final SequelizeORMDetector detector = new SequelizeORMDetector();

    @Test
    void detectsSequelizeUsage() {
        String code = """
                const sequelize = new Sequelize('sqlite::memory:');
                const User = sequelize.define('User', { name: DataTypes.STRING });
                class Post extends Model {}
                User.hasMany(Post);
                User.findAll();
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // connection + User (define) + Post (extends)
        assertTrue(result.nodes().size() >= 3);
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.DATABASE_CONNECTION));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.ENTITY));
        // Association + query edges
        assertTrue(result.edges().size() >= 2);
    }

    @Test
    void noMatchOnNonSequelizeCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "const s = new Sequelize('test');\nsequelize.define('Item', {});";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
