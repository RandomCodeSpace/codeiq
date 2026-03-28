"""Tests for Ktor route detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.kotlin.ktor_routes import KtorRouteDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, path: str = "Application.kt", language: str = "kotlin") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestKtorRouteDetector:
    def setup_method(self):
        self.detector = KtorRouteDetector()

    # --- Positive tests ---

    def test_detects_get_endpoint(self):
        source = """\
fun Application.configureRouting() {
    routing {
        get("/users") {
            call.respondText("users list")
        }
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert endpoints[0].properties["path_pattern"] == "/users"
        assert endpoints[0].properties["framework"] == "ktor"

    def test_detects_multiple_methods(self):
        source = """\
routing {
    get("/items") { call.respond(items) }
    post("/items") { call.respond(HttpStatusCode.Created) }
    put("/items/{id}") { call.respond(HttpStatusCode.OK) }
    delete("/items/{id}") { call.respond(HttpStatusCode.NoContent) }
    patch("/items/{id}") { call.respond(HttpStatusCode.OK) }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 5
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "PUT", "DELETE", "PATCH"}

    def test_detects_routing_module(self):
        source = """\
fun Application.module() {
    routing {
        get("/health") { call.respondText("ok") }
    }
}
"""
        result = self.detector.detect(_ctx(source))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) == 1
        assert modules[0].properties["type"] == "router"

    def test_detects_nested_route_prefix(self):
        source = """\
routing {
    route("/api") {
        get("/users") {
            call.respond(users)
        }
        post("/users") {
            call.respond(HttpStatusCode.Created)
        }
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        paths = {n.properties["path_pattern"] for n in endpoints}
        assert paths == {"/api/users"}

    def test_detects_install_middleware(self):
        source = """\
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(Authentication) {
        basic("auth-basic") { }
    }
}
"""
        result = self.detector.detect(_ctx(source))
        middlewares = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(middlewares) == 2
        features = {n.properties["feature"] for n in middlewares}
        assert features == {"ContentNegotiation", "Authentication"}

    def test_detects_authenticate_guard(self):
        source = """\
routing {
    authenticate("jwt") {
        get("/protected") {
            call.respondText("secret")
        }
    }
}
"""
        result = self.detector.detect(_ctx(source))
        guards = [n for n in result.nodes if n.kind == NodeKind.GUARD]
        assert len(guards) == 1
        assert guards[0].properties["auth_name"] == "jwt"
        assert guards[0].label == "authenticate:jwt"

    # --- Negative tests ---

    def test_empty_file_returns_nothing(self):
        result = self.detector.detect(_ctx("val x = 1\n"))
        assert len(result.nodes) == 0

    def test_non_ktor_code(self):
        source = """\
fun main() {
    println("Hello, World!")
    val items = listOf(1, 2, 3)
    items.forEach { println(it) }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
fun Application.module() {
    install(ContentNegotiation) { json() }
    routing {
        get("/a") { call.respondText("a") }
        post("/b") { call.respondText("b") }
        authenticate("admin") {
            delete("/c") { call.respondText("c") }
        }
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]

    def test_node_id_format(self):
        source = """\
routing {
    get("/test") { call.respondText("test") }
}
"""
        result = self.detector.detect(_ctx(source, path="src/Application.kt"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].id.startswith("ktor:src/Application.kt:GET:/test:")
