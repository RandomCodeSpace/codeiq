"""Tests for TypeORM entity detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.typeorm_entities import TypeORMEntityDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "user.entity.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestTypeORMEntityDetector:
    def setup_method(self):
        self.detector = TypeORMEntityDetector()

    def test_detects_entity_with_table_name(self):
        source = """\
import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';

@Entity('users')
export class User {
    @PrimaryGeneratedColumn()
    id: number;

    @Column()
    name: string;

    @Column()
    email: string;
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "User"
        assert entities[0].properties["table_name"] == "users"
        assert entities[0].properties["framework"] == "typeorm"
        assert "@Entity" in entities[0].annotations

    def test_detects_entity_without_table_name(self):
        source = """\
@Entity()
export class Product {
    @Column()
    title: string;
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].properties["table_name"] == "products"

    def test_detects_columns(self):
        source = """\
@Entity('orders')
export class Order {
    @Column()
    status: string;

    @Column()
    total: number;

    @Column()
    createdAt: Date;
}
"""
        result = self.detector.detect(_ctx(source))
        entity = [n for n in result.nodes if n.kind == NodeKind.ENTITY][0]
        columns = entity.properties.get("columns", [])
        assert "status" in columns
        assert "total" in columns
        assert "createdAt" in columns

    def test_detects_relationships(self):
        source = """\
@Entity('orders')
export class Order {
    @ManyToOne(() => User)
    user: User;

    @OneToMany(() => OrderItem)
    items: OrderItem[];
}
"""
        result = self.detector.detect(_ctx(source))
        maps_edges = [e for e in result.edges if e.kind == EdgeKind.MAPS_TO]
        assert len(maps_edges) == 2
        targets = {e.label for e in maps_edges}
        assert "ManyToOne" in targets
        assert "OneToMany" in targets

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_entity_decorator(self):
        source = """\
export class PlainClass {
    name: string;
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@Entity('accounts')
export class Account {
    @Column()
    balance: number;

    @ManyToOne(() => User)
    owner: User;
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
