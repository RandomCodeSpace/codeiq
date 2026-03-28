"""Tests for Django model detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.python.django_models import DjangoModelDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "models.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestDjangoModelDetector:
    def setup_method(self):
        self.detector = DjangoModelDetector()

    def test_detects_model(self):
        source = """\
from django.db import models

class Author(models.Model):
    name = models.CharField(max_length=100)
    email = models.EmailField()
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].label == "Author"
        assert entities[0].properties["framework"] == "django"
        assert "name" in entities[0].properties["fields"]
        assert entities[0].properties["fields"]["name"] == "CharField"
        assert entities[0].properties["fields"]["email"] == "EmailField"

    def test_detects_relationships(self):
        source = """\
from django.db import models

class Author(models.Model):
    name = models.CharField(max_length=100)

class Book(models.Model):
    title = models.CharField(max_length=200)
    author = models.ForeignKey('Author', on_delete=models.CASCADE)
    tags = models.ManyToManyField('Tag')

    class Meta:
        db_table = 'books'
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 2

        book = [n for n in entities if n.label == "Book"][0]
        assert book.properties["table_name"] == "books"

        depends_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(depends_edges) == 2
        targets = {e.label for e in depends_edges}
        assert "author" in targets
        assert "tags" in targets

    def test_detects_fk_and_one_to_one(self):
        source = """\
from django.db import models

class Profile(models.Model):
    user = models.OneToOneField('User', on_delete=models.CASCADE)
    bio = models.TextField()
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        depends_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(depends_edges) == 1
        assert depends_edges[0].label == "user"
        assert depends_edges[0].target.endswith(":User")

    def test_detects_manager(self):
        source = """\
from django.db import models

class PublishedManager(models.Manager):
    def get_queryset(self):
        return super().get_queryset().filter(status='published')

class Article(models.Model):
    title = models.CharField(max_length=200)
    status = models.CharField(max_length=20)
    objects = PublishedManager()
"""
        result = self.detector.detect(_ctx(source))
        managers = [n for n in result.nodes if n.kind == NodeKind.REPOSITORY]
        assert len(managers) == 1
        assert managers[0].label == "PublishedManager"
        assert managers[0].properties["type"] == "manager"

        queries_edges = [e for e in result.edges if e.kind == EdgeKind.QUERIES]
        assert len(queries_edges) == 1
        assert queries_edges[0].label == "objects"

    def test_detects_meta_ordering(self):
        source = """\
from django.db import models

class Event(models.Model):
    title = models.CharField(max_length=200)
    date = models.DateTimeField()

    class Meta:
        ordering = ['-date']
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert "ordering" in entities[0].properties

    def test_node_id_format(self):
        source = """\
class Foo(models.Model):
    x = models.IntegerField()
"""
        result = self.detector.detect(_ctx(source, path="myapp/models.py"))
        assert result.nodes[0].id == "django:myapp/models.py:model:Foo"

    def test_empty_returns_empty(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_django_model(self):
        source = """\
class Helper:
    def process(self):
        pass
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
from django.db import models

class Author(models.Model):
    name = models.CharField(max_length=100)

class Book(models.Model):
    title = models.CharField(max_length=200)
    author = models.ForeignKey('Author', on_delete=models.CASCADE)
    tags = models.ManyToManyField('Tag')

    class Meta:
        db_table = 'books'
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.source for e in r1.edges] == [e.source for e in r2.edges]
        assert [e.target for e in r1.edges] == [e.target for e in r2.edges]
