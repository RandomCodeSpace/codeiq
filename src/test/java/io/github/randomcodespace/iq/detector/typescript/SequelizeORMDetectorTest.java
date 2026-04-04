package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsSequelizeConnectionNode() {
        String code = "const sequelize = new Sequelize('postgres://user:pass@localhost:5432/db');";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var conn = result.nodes().get(0);
        assertEquals(NodeKind.DATABASE_CONNECTION, conn.getKind());
        assertEquals("Sequelize", conn.getLabel());
        assertEquals("sequelize", conn.getProperties().get("framework"));
    }

    @Test
    void detectsDefineModel() {
        String code = """
                sequelize.define('User', { name: DataTypes.STRING, email: DataTypes.STRING });
                sequelize.define('Post', { title: DataTypes.STRING, body: DataTypes.TEXT });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "User".equals(n.getLabel()) && "define".equals(n.getProperties().get("definition")));
        assertThat(result.nodes()).anyMatch(n -> "Post".equals(n.getLabel()));
    }

    @Test
    void detectsClassExtendsModel() {
        String code = """
                class User extends Model {}
                class Comment extends Model {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertThat(result.nodes()).allMatch(n -> n.getKind() == NodeKind.ENTITY);
        assertThat(result.nodes()).allMatch(n -> "class".equals(n.getProperties().get("definition")));
    }

    @Test
    void detectsAssociationsAsEdges() {
        String code = """
                const sequelize = new Sequelize('sqlite::memory:');
                const User = sequelize.define('User', {});
                const Post = sequelize.define('Post', {});
                User.hasMany(Post);
                Post.belongsTo(User);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/assoc.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.DEPENDS_ON
                && "hasMany".equals(e.getProperties().get("association")));
        assertThat(result.edges()).anyMatch(e -> "belongsTo".equals(e.getProperties().get("association")));
    }

    @Test
    void detectsQueryOperations() {
        String code = """
                const sequelize = new Sequelize('sqlite::memory:');
                const User = sequelize.define('User', {});
                User.findAll({ where: {} });
                User.findOne({ where: { id: 1 } });
                User.create({ name: 'Alice' });
                User.destroy({ where: {} });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/repo.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.QUERIES
                && "findAll".equals(e.getProperties().get("operation")));
        assertThat(result.edges()).anyMatch(e -> "destroy".equals(e.getProperties().get("operation")));
    }

    @Test
    void doesNotDuplicateClassAndDefineModels() {
        // If same model defined via define() and class extends, don't duplicate
        String code = """
                const User = sequelize.define('User', {});
                class User extends Model {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long userCount = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY && "User".equals(n.getLabel()))
                .count();
        assertEquals(1, userCount, "User should not be duplicated from both define and class extends");
    }

    @Test
    void noMatchOnNonSequelizeCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
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
                const s = new Sequelize('test');
                sequelize.define('Item', {});
                Item.findAll();
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("sequelize_orm", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
