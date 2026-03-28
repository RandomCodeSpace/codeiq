"""Tests for NestJS controller detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.typescript.nestjs_controllers import NestJSControllerDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "user.controller.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestNestJSControllerDetector:
    def setup_method(self):
        self.detector = NestJSControllerDetector()

    def test_detects_controller_class(self):
        source = """\
@Controller('users')
export class UserController {

    @Get()
    findAll() {
        return this.userService.findAll();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert classes[0].label == "UserController"
        assert "@Controller" in classes[0].annotations

    def test_detects_routes_with_base_path(self):
        source = """\
@Controller('users')
export class UserController {

    @Get()
    findAll() {}

    @Post()
    create() {}

    @Get('/:id')
    findOne() {}

    @Delete('/:id')
    remove() {}
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 4
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "DELETE"}

    def test_correct_full_paths(self):
        source = """\
@Controller('api/orders')
export class OrderController {

    @Get('/:id')
    findOne() {}
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert "/api/orders/:id" in endpoints[0].properties["path_pattern"]

    def test_creates_exposes_edges(self):
        source = """\
@Controller('items')
export class ItemController {

    @Get()
    list() {}
}
"""
        result = self.detector.detect(_ctx(source))
        expose_edges = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(expose_edges) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_controller_decorator(self):
        source = """\
export class PlainService {
    doWork() {}
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@Controller('tasks')
export class TaskController {
    @Get()
    findAll() {}
    @Post()
    create() {}
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
