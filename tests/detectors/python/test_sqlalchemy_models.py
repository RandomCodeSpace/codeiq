"""Tests for SQLAlchemy model detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.python.sqlalchemy_models import SQLAlchemyModelDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "models.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestSQLAlchemyModelDetector:
    def setup_method(self):
        self.detector = SQLAlchemyModelDetector()

    def test_detects_model_with_tablename(self):
        source = """\
from sqlalchemy import Column, Integer, String
from sqlalchemy.orm import declarative_base

Base = declarative_base()

class User(Base):
    __tablename__ = 'users'
    id = Column(Integer, primary_key=True)
    name = Column(String(50))
    email = Column(String(120))
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "User"
        assert entities[0].properties["table_name"] == "users"
        assert entities[0].properties["framework"] == "sqlalchemy"
        assert "name" in entities[0].properties["columns"]
        assert "email" in entities[0].properties["columns"]

    def test_detects_model_without_tablename(self):
        source = """\
class Product(Base):
    id = Column(Integer, primary_key=True)
    title = Column(String)
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        # Default table name is lowercase class name + 's'
        assert entities[0].properties["table_name"] == "products"

    def test_detects_relationships(self):
        source = """\
class User(Base):
    __tablename__ = 'users'
    id = Column(Integer, primary_key=True)
    orders = relationship("Order", back_populates="user")

class Order(Base):
    __tablename__ = 'orders'
    id = Column(Integer, primary_key=True)
    user = relationship("User", back_populates="orders")
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 2
        maps_edges = [e for e in result.edges if e.kind == EdgeKind.MAPS_TO]
        assert len(maps_edges) >= 2

    def test_detects_db_model(self):
        source = """\
class Post(db.Model):
    __tablename__ = 'posts'
    id = Column(Integer, primary_key=True)
    title = Column(String(200))
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].properties["table_name"] == "posts"

    def test_detects_mapped_column(self):
        source = """\
class Item(Base):
    __tablename__ = 'items'
    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert "name" in entities[0].properties["columns"]

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_model_class(self):
        source = """\
class Helper:
    def process(self):
        pass
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
class Account(Base):
    __tablename__ = 'accounts'
    id = Column(Integer, primary_key=True)
    balance = Column(Float)
    user = relationship("User")
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
