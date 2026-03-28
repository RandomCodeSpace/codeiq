"""Tests for NestJS guards detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.typescript.nestjs_guards import NestJSGuardsDetector
from osscodeiq.models.graph import NodeKind


def _ctx(content: str, file_path: str = "auth.guard.ts") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language="typescript",
        content=content.encode("utf-8"),
        module_name="test-module",
    )


class TestNestJSGuardsDetector:
    def setup_method(self):
        self.detector = NestJSGuardsDetector()

    def test_name_and_languages(self):
        assert self.detector.name == "typescript.nestjs_guards"
        assert self.detector.supported_languages == ("typescript",)

    def test_detect_use_guards_single(self):
        source = """\
@UseGuards(JwtAuthGuard)
@Get('profile')
async getProfile() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        guard = guards[0]
        assert guard.properties["auth_type"] == "nestjs_guard"
        assert guard.properties["guard_name"] == "JwtAuthGuard"
        assert guard.id == "auth:auth.guard.ts:UseGuards(JwtAuthGuard):1"
        assert guard.label == "UseGuards(JwtAuthGuard)"
        assert guard.properties["roles"] == []

    def test_detect_use_guards_multiple(self):
        source = """\
@UseGuards(JwtAuthGuard, RolesGuard)
@Get('admin')
async getAdmin() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 2
        guard_names = {g.properties["guard_name"] for g in guards}
        assert guard_names == {"JwtAuthGuard", "RolesGuard"}

    def test_detect_roles_decorator(self):
        source = """\
@Roles('admin', 'user')
@UseGuards(RolesGuard)
@Get('dashboard')
async getDashboard() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        roles_nodes = [g for g in guards if "@Roles" in g.annotations]
        assert len(roles_nodes) == 1
        roles_node = roles_nodes[0]
        assert roles_node.properties["roles"] == ["admin", "user"]
        assert roles_node.properties["auth_type"] == "nestjs_guard"
        assert roles_node.id == "auth:auth.guard.ts:Roles:1"

    def test_detect_can_activate(self):
        source = """\
@Injectable()
export class JwtAuthGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    return true;
  }
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        ca_nodes = [g for g in guards if g.properties.get("guard_impl") == "canActivate"]
        assert len(ca_nodes) == 1
        assert ca_nodes[0].label == "canActivate()"
        assert ca_nodes[0].properties["auth_type"] == "nestjs_guard"
        assert ca_nodes[0].properties["roles"] == []

    def test_detect_auth_guard(self):
        source = """\
export class JwtAuthGuard extends AuthGuard('jwt') {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        ag_nodes = [g for g in guards if g.properties.get("strategy") == "jwt"]
        assert len(ag_nodes) == 1
        assert ag_nodes[0].label == "AuthGuard('jwt')"
        assert ag_nodes[0].properties["auth_type"] == "nestjs_guard"
        assert ag_nodes[0].id == "auth:auth.guard.ts:AuthGuard(jwt):1"
        assert ag_nodes[0].properties["roles"] == []

    def test_detect_auth_guard_local(self):
        source = """\
export class LocalAuthGuard extends AuthGuard('local') {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        ag_nodes = [g for g in guards if g.properties.get("strategy") == "local"]
        assert len(ag_nodes) == 1
        assert ag_nodes[0].label == "AuthGuard('local')"

    def test_empty_file(self):
        result = self.detector.detect(_ctx(""))
        assert result.nodes == []
        assert result.edges == []

    def test_no_guards(self):
        source = """\
@Controller('users')
export class UsersController {
  @Get()
  findAll() {}
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 0

    def test_combined_guards_and_roles(self):
        source = """\
@Controller('admin')
export class AdminController {
  @UseGuards(JwtAuthGuard, RolesGuard)
  @Roles('admin')
  @Get('stats')
  async getStats() {}

  @UseGuards(JwtAuthGuard)
  @Get('profile')
  async getProfile() {}
}
"""
        result = self.detector.detect(_ctx(source, file_path="admin.controller.ts"))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        # 2 from first @UseGuards + 1 @Roles + 1 from second @UseGuards = 4
        assert len(guards) == 4

    def test_line_numbers_are_correct(self):
        source = """\
import { UseGuards } from '@nestjs/common';

@UseGuards(JwtAuthGuard)
@Get('first')
async first() {}

@UseGuards(RolesGuard)
@Get('second')
async second() {}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 2
        lines = sorted(g.location.line_start for g in guards)
        assert lines == [3, 7]

    def test_returns_detector_result(self):
        result = self.detector.detect(_ctx(""))
        assert isinstance(result, DetectorResult)
