"""Tests for C# detectors."""

from osscodeiq.detectors.base import DetectorContext
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content, file_path="Test.cs"):
    return DetectorContext(
        file_path=file_path,
        language="csharp",
        content=content,
        module_name="MyApp",
    )


class TestCSharpStructuresDetector:
    def test_detect_class_and_interface(self):
        source = b'''
namespace MyApp.Services;

public interface IUserService {
    Task<User> GetUserAsync(int id);
}

public class UserService : IUserService {
    public async Task<User> GetUserAsync(int id) { return null; }
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        interfaces = [n for n in result.nodes if n.kind == NodeKind.INTERFACE]
        assert len(classes) >= 1
        assert len(interfaces) >= 1

    def test_detect_aspnet_endpoints(self):
        source = b'''
[ApiController]
[Route("api/[controller]")]
public class UsersController : ControllerBase {
    [HttpGet]
    public IActionResult GetAll() { return Ok(); }

    [HttpPost]
    public IActionResult Create([FromBody] User user) { return Ok(); }
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 2

    def test_detect_namespace(self):
        source = b'''
namespace MyApp.Domain;

public class Order {
    public int Id { get; set; }
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 1
        assert modules[0].label == "MyApp.Domain"

    def test_detect_enum(self):
        source = b'''
namespace MyApp;

public enum OrderStatus {
    Pending,
    Confirmed,
    Shipped,
    Delivered
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        enums = [n for n in result.nodes if n.kind == NodeKind.ENUM]
        assert len(enums) == 1
        assert enums[0].label == "OrderStatus"

    def test_detect_inheritance_edges(self):
        source = b'''
public class Dog : Animal, IWalkable, IFeedable {
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        implements_edges = [e for e in result.edges if e.kind == EdgeKind.IMPLEMENTS]
        assert len(extends_edges) >= 1
        assert extends_edges[0].target == "*:Animal"
        assert len(implements_edges) >= 2

    def test_detect_using_imports(self):
        source = b'''
using System;
using System.Collections.Generic;
using MyApp.Services;

public class Foo {}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        import_edges = [e for e in result.edges if e.kind == EdgeKind.IMPORTS]
        targets = {e.target for e in import_edges}
        assert "System" in targets
        assert "System.Collections.Generic" in targets
        assert "MyApp.Services" in targets

    def test_detect_azure_function(self):
        source = b'''
namespace MyApp.Functions;

public class UserFunctions {
    [Function("GetUsers")]
    public HttpResponseData Run(
        [HttpTrigger(AuthorizationLevel.Anonymous, "get")] HttpRequestData req) {
        return req.CreateResponse(HttpStatusCode.OK);
    }
}
'''
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        result = CSharpStructuresDetector().detect(_ctx(source))
        funcs = [n for n in result.nodes if n.kind == NodeKind.AZURE_FUNCTION]
        assert len(funcs) >= 1
        assert any("GetUsers" in n.label for n in funcs)

    def test_supported_languages(self):
        from osscodeiq.detectors.csharp.csharp_structures import CSharpStructuresDetector

        d = CSharpStructuresDetector()
        assert "csharp" in d.supported_languages
