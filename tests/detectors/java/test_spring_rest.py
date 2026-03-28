"""Tests for Spring REST endpoint detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.spring_rest import SpringRestDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "UserController.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestSpringRestDetector:
    def setup_method(self):
        self.detector = SpringRestDetector()

    def test_detects_get_mapping(self):
        source = """\
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.findById(id);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) >= 1
        endpoint = result.nodes[0]
        assert endpoint.kind == NodeKind.ENDPOINT
        assert endpoint.properties["http_method"] == "GET"
        assert "/api/users/{id}" in endpoint.properties["path"]

    def test_detects_post_mapping(self):
        source = """\
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @PostMapping
    public Order createOrder(@RequestBody Order order) {
        return orderService.save(order);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) >= 1
        endpoint = result.nodes[0]
        assert endpoint.properties["http_method"] == "POST"

    def test_detects_multiple_methods(self):
        source = """\
@RestController
@RequestMapping("/api/items")
public class ItemController {

    @GetMapping
    public List<Item> listItems() {
        return itemService.findAll();
    }

    @PostMapping
    public Item createItem(@RequestBody Item item) {
        return itemService.save(item);
    }

    @PutMapping("/{id}")
    public Item updateItem(@PathVariable Long id, @RequestBody Item item) {
        return itemService.update(id, item);
    }

    @DeleteMapping("/{id}")
    public void deleteItem(@PathVariable Long id) {
        itemService.delete(id);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) >= 4
        methods = {n.properties["http_method"] for n in result.nodes}
        assert methods == {"GET", "POST", "PUT", "DELETE"}

    def test_detects_request_mapping_with_method(self):
        source = """\
@RestController
public class LegacyController {

    @RequestMapping(value = "/legacy", method = RequestMethod.POST)
    public void legacyEndpoint() {}
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) >= 1
        assert result.nodes[0].properties["http_method"] == "POST"

    def test_creates_exposes_edges(self):
        source = """\
@RestController
public class MyController {

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}
"""
        result = self.detector.detect(_ctx(source))
        expose_edges = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(expose_edges) >= 1

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class Foo { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_class_returns_nothing(self):
        source = "package com.example;\nimport java.util.List;\n"
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@RestController
@RequestMapping("/api/v1")
public class ApiController {

    @GetMapping("/items")
    public List<Item> getItems() { return List.of(); }

    @PostMapping("/items")
    public Item createItem(@RequestBody Item item) { return item; }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
