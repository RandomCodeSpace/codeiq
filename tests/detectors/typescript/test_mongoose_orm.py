"""Tests for Mongoose ODM detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.typescript.mongoose_orm import MongooseORMDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "src/models.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestMongooseORMDetector:
    def setup_method(self):
        self.detector = MongooseORMDetector()

    # --- Model / Entity detection ---

    def test_detects_model(self):
        source = """\
const userSchema = new mongoose.Schema({
    name: String,
    email: String,
});
const User = mongoose.model('User', userSchema);
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        # One schema node + one model node
        models = [n for n in entities if n.properties.get("definition") == "model"]
        assert len(models) == 1
        assert models[0].label == "User"
        assert models[0].properties["framework"] == "mongoose"
        assert models[0].id == "mongoose:src/models.ts:model:User"

    def test_detects_schema_definition(self):
        source = """\
const userSchema = new Schema({
    name: String,
    email: String,
});
"""
        result = self.detector.detect(_ctx(source))
        schemas = [n for n in result.nodes if n.properties.get("definition") == "schema"]
        assert len(schemas) == 1
        assert schemas[0].label == "userSchema"

    def test_detects_mongoose_schema_definition(self):
        source = """\
const postSchema = new mongoose.Schema({
    title: String,
    body: String,
});
"""
        result = self.detector.detect(_ctx(source))
        schemas = [n for n in result.nodes if n.properties.get("definition") == "schema"]
        assert len(schemas) == 1
        assert schemas[0].label == "postSchema"

    def test_detects_multiple_models(self):
        source = """\
const User = mongoose.model('User', userSchema);
const Post = mongoose.model('Post', postSchema);
const Comment = mongoose.model('Comment', commentSchema);
"""
        result = self.detector.detect(_ctx(source))
        models = [n for n in result.nodes if n.properties.get("definition") == "model"]
        labels = {m.label for m in models}
        assert labels == {"User", "Post", "Comment"}

    # --- Connection detection ---

    def test_detects_connection(self):
        source = """\
mongoose.connect('mongodb://localhost/mydb');
"""
        result = self.detector.detect(_ctx(source))
        connections = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(connections) == 1
        assert connections[0].label == "mongoose.connect"
        assert connections[0].properties["framework"] == "mongoose"

    # --- Queries / Operations detection ---

    def test_detects_queries(self):
        source = """\
const User = mongoose.model('User', userSchema);

const users = await User.find({ active: true });
const user = await User.findOne({ email: 'test@test.com' });
const byId = await User.findById('123');
await User.create({ name: 'Alice' });
await User.updateOne({ _id: '123' }, { name: 'Bob' });
await User.deleteOne({ _id: '123' });
"""
        result = self.detector.detect(_ctx(source))
        query_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        assert len(query_edges) == 6
        operations = {e.properties["operation"] for e in query_edges}
        assert "find" in operations
        assert "findOne" in operations
        assert "findById" in operations
        assert "create" in operations
        assert "updateOne" in operations
        assert "deleteOne" in operations

    def test_detects_virtuals(self):
        source = """\
const userSchema = new mongoose.Schema({
    firstName: String,
    lastName: String,
});
userSchema.virtual('fullName');
"""
        result = self.detector.detect(_ctx(source))
        schemas = [n for n in result.nodes if n.properties.get("definition") == "schema"]
        assert len(schemas) == 1
        assert "fullName" in schemas[0].properties.get("virtuals", [])

    def test_detects_lifecycle_hooks(self):
        source = """\
const userSchema = new mongoose.Schema({ name: String });
userSchema.pre('save', function(next) { next(); });
userSchema.post('save', function(doc) { console.log(doc); });
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) == 2
        hook_labels = {e.label for e in events}
        assert "pre:save" in hook_labels
        assert "post:save" in hook_labels

    def test_detects_pre_validate_hook(self):
        source = """\
const userSchema = new mongoose.Schema({ name: String });
userSchema.pre('validate', function(next) { next(); });
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) == 1
        assert events[0].properties["hook_type"] == "pre"
        assert events[0].properties["event"] == "validate"

    # --- Negative cases ---

    def test_empty_returns_empty(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_mongoose_code(self):
        source = """\
class User {
    find() { return []; }
}
const db = new Database();
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_mongoose_without_operations(self):
        source = """\
import mongoose from 'mongoose';
// Just importing, no usage
const config = { db: 'mongodb://localhost' };
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    # --- Determinism ---

    def test_determinism(self):
        source = """\
mongoose.connect('mongodb://localhost/mydb');
const userSchema = new mongoose.Schema({ name: String });
userSchema.pre('save', function(next) { next(); });
const User = mongoose.model('User', userSchema);
await User.find({});
await User.create({ name: 'test' });
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.label for e in r1.edges] == [e.label for e in r2.edges]
