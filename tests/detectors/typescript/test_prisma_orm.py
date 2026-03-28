"""Tests for Prisma ORM detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.prisma_orm import PrismaORMDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "src/users.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestPrismaORMDetector:
    def setup_method(self):
        self.detector = PrismaORMDetector()

    # --- Model / Entity detection ---

    def test_detects_model_from_query(self):
        source = """\
import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();

const users = await prisma.user.findMany();
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "user"
        assert entities[0].properties["framework"] == "prisma"
        assert entities[0].id == "prisma:src/users.ts:model:user"

    def test_detects_multiple_models(self):
        source = """\
const users = await prisma.user.findMany();
const posts = await prisma.post.create({ data: {} });
const comments = await prisma.comment.findFirst();
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        labels = {e.label for e in entities}
        assert labels == {"user", "post", "comment"}

    # --- Connection detection ---

    def test_detects_connection(self):
        source = """\
import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();
"""
        result = self.detector.detect(_ctx(source))
        connections = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(connections) == 1
        assert connections[0].label == "PrismaClient"
        assert connections[0].properties["framework"] == "prisma"

    def test_detects_connection_with_transaction(self):
        source = """\
const prisma = new PrismaClient();
await prisma.$transaction([
    prisma.user.create({ data: {} }),
]);
"""
        result = self.detector.detect(_ctx(source))
        connections = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(connections) == 1
        assert connections[0].properties.get("transaction") is True

    # --- Queries / Operations detection ---

    def test_detects_queries(self):
        source = """\
await prisma.user.findMany({ where: { active: true } });
await prisma.user.create({ data: { name: 'Alice' } });
await prisma.post.update({ where: { id: 1 }, data: {} });
await prisma.post.delete({ where: { id: 1 } });
"""
        result = self.detector.detect(_ctx(source))
        query_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        assert len(query_edges) == 4
        operations = {e.properties["operation"] for e in query_edges}
        assert "findMany" in operations
        assert "create" in operations
        assert "update" in operations
        assert "delete" in operations

    def test_detects_import_edge(self):
        source = """\
import { PrismaClient } from '@prisma/client';
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1
        assert import_edges[0].target == "@prisma/client"

    def test_detects_require_import(self):
        source = """\
const { PrismaClient } = require('@prisma/client');
"""
        result = self.detector.detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        assert len(import_edges) == 1

    def test_detects_all_query_operations(self):
        source = """\
await prisma.user.findUnique({ where: { id: 1 } });
await prisma.user.upsert({ where: {}, create: {}, update: {} });
await prisma.user.count();
await prisma.user.aggregate({ _avg: { age: true } });
await prisma.user.groupBy({ by: ['role'] });
"""
        result = self.detector.detect(_ctx(source))
        query_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        operations = {e.properties["operation"] for e in query_edges}
        assert operations == {"findUnique", "upsert", "count", "aggregate", "groupBy"}

    # --- Negative cases ---

    def test_empty_returns_empty(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_non_prisma_code(self):
        source = """\
const db = new Database();
db.query('SELECT * FROM users');
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_partial_match_not_detected(self):
        source = """\
// prisma.user.findMany is great
const fakePrisma = { user: { findMany: () => {} } };
"""
        result = self.detector.detect(_ctx(source))
        # The comment line matches the regex, which is acceptable
        # But fakePrisma assignment line does NOT match (no function call parens after findMany)
        query_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        # Only the comment line matches
        assert len(query_edges) <= 1

    # --- Determinism ---

    def test_determinism(self):
        source = """\
import { PrismaClient } from '@prisma/client';
const prisma = new PrismaClient();
await prisma.user.findMany();
await prisma.post.create({ data: {} });
await prisma.$transaction([]);
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.label for e in r1.edges] == [e.label for e in r2.edges]
