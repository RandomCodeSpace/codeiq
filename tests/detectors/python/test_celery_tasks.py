"""Tests for Celery task detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.python.celery_tasks import CeleryTaskDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "tasks.py", language: str = "python") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestCeleryTaskDetector:
    def setup_method(self):
        self.detector = CeleryTaskDetector()

    def test_detects_app_task(self):
        source = """\
from celery import Celery
app = Celery('tasks')

@app.task
def send_email(to, subject, body):
    mail.send(to, subject, body)
"""
        result = self.detector.detect(_ctx(source))
        queues = [n for n in result.nodes if n.kind == NodeKind.QUEUE]
        assert len(queues) == 1
        assert "send_email" in queues[0].properties["task_name"]
        methods = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        assert len(methods) == 1
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) == 1

    def test_detects_shared_task(self):
        source = """\
from celery import shared_task

@shared_task
def process_payment(order_id):
    do_payment(order_id)
"""
        result = self.detector.detect(_ctx(source))
        queues = [n for n in result.nodes if n.kind == NodeKind.QUEUE]
        assert len(queues) == 1
        assert "process_payment" in queues[0].properties["task_name"]

    def test_detects_named_task(self):
        source = """\
@app.task(name='orders.process_order')
def process_order(order_id):
    handle_order(order_id)
"""
        result = self.detector.detect(_ctx(source))
        queues = [n for n in result.nodes if n.kind == NodeKind.QUEUE]
        assert len(queues) == 1
        assert queues[0].properties["task_name"] == "orders.process_order"

    def test_detects_task_invocation(self):
        source = """\
def trigger_email():
    send_email.delay('user@example.com', 'Welcome', 'Hello!')
"""
        result = self.detector.detect(_ctx(source))
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) >= 1

    def test_detects_apply_async(self):
        source = """\
def enqueue():
    process_order.apply_async(args=[order_id], countdown=60)
"""
        result = self.detector.detect(_ctx(source))
        produce_edges = [e for e in result.edges if e.kind == EdgeKind.PRODUCES]
        assert len(produce_edges) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("x = 1\nprint(x)\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_task_patterns(self):
        source = """\
def plain_function():
    return "not a task"
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@app.task
def task_a():
    pass

@shared_task
def task_b():
    pass
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
