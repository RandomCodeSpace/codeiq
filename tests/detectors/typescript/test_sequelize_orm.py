"""Tests for Sequelize ORM detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.sequelize_orm import SequelizeORMDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "src/models.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestSequelizeORMDetector:
    def setup_method(self):
        self.detector = SequelizeORMDetector()

    # --- Model / Entity detection ---

    def test_detects_model_via_define(self):
        source = """\
const User = sequelize.define('User', {
    name: DataTypes.STRING,
    email: DataTypes.STRING,
});
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "User"
        assert entities[0].properties["framework"] == "sequelize"
        assert entities[0].properties["definition"] == "define"
        assert entities[0].id == "sequelize:src/models.ts:model:User"

    def test_detects_model_via_class_extends(self):
        source = """\
class User extends Model {
    declare id: number;
    declare name: string;
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "User"
        assert entities[0].properties["definition"] == "class"

    def test_detects_multiple_models(self):
        source = """\
const User = sequelize.define('User', { name: DataTypes.STRING });
const Post = sequelize.define('Post', { title: DataTypes.STRING });
class Comment extends Model {}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        labels = {e.label for e in entities}
        assert labels == {"User", "Post", "Comment"}

    # --- Connection detection ---

    def test_detects_connection(self):
        source = """\
const sequelize = new Sequelize('sqlite::memory:');
"""
        result = self.detector.detect(_ctx(source))
        connections = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(connections) == 1
        assert connections[0].label == "Sequelize"
        assert connections[0].properties["framework"] == "sequelize"

    def test_detects_sequelize_sequelize_connection(self):
        source = """\
const sequelize = new Sequelize.Sequelize('postgres://localhost/db');
"""
        result = self.detector.detect(_ctx(source))
        connections = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(connections) == 1

    # --- Queries / Operations detection ---

    def test_detects_queries(self):
        source = """\
class User extends Model {}

const users = await User.findAll({ where: { active: true } });
const user = await User.findOne({ where: { id: 1 } });
await User.create({ name: 'Alice' });
await User.update({ name: 'Bob' }, { where: { id: 1 } });
await User.destroy({ where: { id: 1 } });
"""
        result = self.detector.detect(_ctx(source))
        query_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        assert len(query_edges) == 5
        operations = {e.properties["operation"] for e in query_edges}
        assert "findAll" in operations
        assert "findOne" in operations
        assert "create" in operations
        assert "update" in operations
        assert "destroy" in operations

    def test_detects_associations(self):
        source = """\
class User extends Model {}
class Post extends Model {}
class Tag extends Model {}

User.hasMany(Post);
Post.belongsTo(User);
Post.belongsToMany(Tag);
"""
        result = self.detector.detect(_ctx(source))
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 3
        assoc_types = {e.label for e in dep_edges}
        assert "hasMany" in assoc_types
        assert "belongsTo" in assoc_types
        assert "belongsToMany" in assoc_types

    def test_association_targets_correct_models(self):
        source = """\
class User extends Model {}
class Post extends Model {}

User.hasMany(Post);
"""
        result = self.detector.detect(_ctx(source))
        dep_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(dep_edges) == 1
        assert dep_edges[0].source == "sequelize:src/models.ts:model:User"
        assert dep_edges[0].target == "sequelize:src/models.ts:model:Post"

    # --- Negative cases ---

    def test_empty_returns_empty(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_sequelize_code(self):
        source = """\
class User {
    name: string;
    findAll() { return []; }
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 0

    def test_model_without_extends_not_detected(self):
        source = """\
class User {
    static findAll() {}
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 0

    # --- Determinism ---

    def test_determinism(self):
        source = """\
const sequelize = new Sequelize('sqlite::memory:');
const User = sequelize.define('User', { name: DataTypes.STRING });
class Post extends Model {}
User.hasMany(Post);
await User.findAll();
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.label for e in r1.edges] == [e.label for e in r2.edges]
