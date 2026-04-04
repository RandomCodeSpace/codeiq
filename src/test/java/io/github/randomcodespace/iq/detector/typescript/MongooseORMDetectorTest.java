package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsConnectionNode() {
        String code = "mongoose.connect('mongodb://localhost:27017/mydb');";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var conn = result.nodes().get(0);
        assertEquals(NodeKind.DATABASE_CONNECTION, conn.getKind());
        assertEquals("mongoose.connect", conn.getLabel());
        assertEquals("mongoose", conn.getProperties().get("framework"));
    }

    @Test
    void detectsSchemaAsEntity() {
        String code = """
                const postSchema = new mongoose.Schema({
                    title: { type: String, required: true },
                    body: String,
                    createdAt: Date
                });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/post.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var schema = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY)
                .findFirst();
        assertTrue(schema.isPresent());
        assertEquals("postSchema", schema.get().getLabel());
        assertEquals("schema", schema.get().getProperties().get("definition"));
    }

    @Test
    void detectsModelAsEntity() {
        String code = """
                const User = mongoose.model('User', userSchema);
                const Post = mongoose.model('Post', postSchema);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/models.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertThat(result.nodes()).allMatch(n -> n.getKind() == NodeKind.ENTITY);
        assertThat(result.nodes()).allMatch(n -> "model".equals(n.getProperties().get("definition")));
        assertThat(result.nodes()).anyMatch(n -> "User".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "Post".equals(n.getLabel()));
    }

    @Test
    void detectsQueryOperationsAsEdges() {
        String code = """
                const userSchema = new Schema({ name: String });
                const User = mongoose.model('User', userSchema);
                User.find({ active: true });
                User.findById(id);
                User.updateOne({ _id: id }, data);
                User.deleteMany({});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.repo.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.edges().isEmpty());
        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.QUERIES
                && "find".equals(e.getProperties().get("operation")));
        assertThat(result.edges()).anyMatch(e -> "deleteMany".equals(e.getProperties().get("operation")));
    }

    @Test
    void detectsSchemaHooksAsEvents() {
        String code = """
                const userSchema = new mongoose.Schema({ name: String });
                userSchema.pre('save', async function(next) { next(); });
                userSchema.post('find', function(docs) {});
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.EVENT
                && "pre:save".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.EVENT
                && "post:find".equals(n.getLabel()));
    }

    @Test
    void detectsVirtuals() {
        String code = """
                const userSchema = new mongoose.Schema({
                    firstName: String,
                    lastName: String
                });
                userSchema.virtual('fullName').get(function() {
                    return this.firstName + ' ' + this.lastName;
                });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var schemaNode = result.nodes().stream()
                .filter(n -> "userSchema".equals(n.getLabel()))
                .findFirst();
        assertTrue(schemaNode.isPresent());
        assertNotNull(schemaNode.get().getProperties().get("virtuals"));
    }

    @Test
    void noMatchOnNonMongooseCode() {
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
        String code = "mongoose.connect('mongodb://localhost');\nconst s = new Schema({});\nconst M = mongoose.model('M', s);";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("mongoose_orm", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
