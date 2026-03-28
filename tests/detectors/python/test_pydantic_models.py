"""Tests for Pydantic model detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.python.pydantic_models import PydanticModelDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "models.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestPydanticModelDetector:
    def setup_method(self):
        self.detector = PydanticModelDetector()

    def test_detects_model(self):
        source = """\
from pydantic import BaseModel, Field

class User(BaseModel):
    name: str
    email: str = Field(..., description="Email")
    age: int = 0
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "User"
        assert entities[0].properties["framework"] == "pydantic"
        assert "name" in entities[0].properties["fields"]
        assert "email" in entities[0].properties["fields"]
        assert "age" in entities[0].properties["fields"]
        assert entities[0].properties["field_types"]["name"] == "str"
        assert entities[0].properties["field_types"]["age"] == "int"

    def test_detects_settings(self):
        source = """\
from pydantic_settings import BaseSettings

class UserSettings(BaseSettings):
    db_url: str
    debug: bool
"""
        result = self.detector.detect(_ctx(source))
        configs = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION]
        assert len(configs) == 1
        assert configs[0].label == "UserSettings"
        assert configs[0].properties["base_class"] == "BaseSettings"
        assert "db_url" in configs[0].properties["fields"]

    def test_detects_validators(self):
        source = """\
from pydantic import BaseModel, validator

class Item(BaseModel):
    price: float

    @validator('price')
    def price_must_be_positive(cls, v):
        if v <= 0:
            raise ValueError('must be positive')
        return v
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert "price" in entities[0].annotations

    def test_detects_field_validator(self):
        source = """\
from pydantic import BaseModel, field_validator

class Item(BaseModel):
    name: str

    @field_validator('name')
    @classmethod
    def name_must_not_be_empty(cls, v):
        if not v:
            raise ValueError('must not be empty')
        return v
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert "name" in entities[0].annotations

    def test_detects_config_class(self):
        source = """\
from pydantic import BaseModel

class User(BaseModel):
    name: str

    class Config:
        orm_mode = True
        from_attributes = True
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert "config" in entities[0].properties
        assert entities[0].properties["config"]["orm_mode"] == "True"

    def test_detects_inheritance(self):
        source = """\
from pydantic import BaseModel

class BaseUser(BaseModel):
    name: str

class AdminUser(BaseUser):
    role: str
"""
        result = self.detector.detect(_ctx(source))
        # BaseUser detected as BaseModel subclass
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) >= 1
        assert entities[0].label == "BaseUser"

    def test_node_id_format(self):
        source = """\
class Foo(BaseModel):
    x: int
"""
        result = self.detector.detect(_ctx(source, path="app/schemas.py"))
        assert result.nodes[0].id == "pydantic:app/schemas.py:model:Foo"

    def test_empty_returns_empty(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_pydantic_class(self):
        source = """\
class Helper:
    def process(self):
        pass
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
from pydantic import BaseModel, Field

class User(BaseModel):
    name: str
    email: str = Field(..., description="Email")
    age: int = 0

class UserSettings(BaseSettings):
    db_url: str
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.source for e in r1.edges] == [e.source for e in r2.edges]
