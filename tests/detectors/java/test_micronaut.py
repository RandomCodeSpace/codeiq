"""Tests for Micronaut framework detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.java.micronaut import MicronautDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "HelloController.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestMicronautDetector:
    def setup_method(self):
        self.detector = MicronautDetector()

    # --- Positive tests ---

    def test_detects_controller(self):
        source = """\
import io.micronaut.http.annotation.Controller;

@Controller("/hello")
public class HelloController {
}
"""
        result = self.detector.detect(_ctx(source))
        ctrl_nodes = [n for n in result.nodes if n.kind == NodeKind.CLASS and "@Controller" in n.annotations]
        assert len(ctrl_nodes) == 1
        assert ctrl_nodes[0].properties["framework"] == "micronaut"
        assert ctrl_nodes[0].properties["path"] == "/hello"

    def test_detects_get_endpoint(self):
        source = """\
@Controller("/api")
public class ApiController {

    @Get("/items")
    public List<Item> getItems() {
        return itemService.findAll();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "GET"
        assert "/api/items" in endpoints[0].properties["path"]

    def test_detects_post_endpoint(self):
        source = """\
@Controller("/api")
public class ApiController {

    @Post("/items")
    public Item createItem(@Body Item item) {
        return itemService.save(item);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["http_method"] == "POST"

    def test_detects_multiple_endpoints(self):
        source = """\
@Controller("/api/users")
public class UserController {

    @Get
    public List<User> listUsers() { return List.of(); }

    @Post
    public User createUser(@Body User u) { return u; }

    @Put("/{id}")
    public User updateUser(Long id, @Body User u) { return u; }

    @Delete("/{id}")
    public void deleteUser(Long id) {}
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 4
        methods = {n.properties["http_method"] for n in endpoints}
        assert methods == {"GET", "POST", "PUT", "DELETE"}

    def test_creates_exposes_edges(self):
        source = """\
@Controller("/api")
public class MyController {

    @Get("/data")
    public String getData() { return "data"; }
}
"""
        result = self.detector.detect(_ctx(source))
        exposes = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(exposes) >= 1

    def test_detects_singleton_scope(self):
        source = """\
@Singleton
public class CacheService {
    public Object get(String key) { return null; }
}
"""
        result = self.detector.detect(_ctx(source))
        scope_nodes = [n for n in result.nodes if n.kind == NodeKind.MIDDLEWARE]
        assert len(scope_nodes) >= 1
        assert scope_nodes[0].properties["bean_scope"] == "Singleton"

    def test_detects_prototype_scope(self):
        source = """\
@Prototype
public class RequestHandler {
    public void handle() {}
}
"""
        result = self.detector.detect(_ctx(source))
        scope_nodes = [n for n in result.nodes if "@Prototype" in n.annotations]
        assert len(scope_nodes) == 1

    def test_detects_client(self):
        source = """\
@Singleton
public class GatewayService {

    @Client("/user-service")
    UserClient userClient;
}
"""
        result = self.detector.detect(_ctx(source))
        client_nodes = [n for n in result.nodes if "@Client" in n.annotations]
        assert len(client_nodes) == 1
        assert client_nodes[0].properties["client_target"] == "/user-service"
        depends_edges = [e for e in result.edges if e.kind == EdgeKind.DEPENDS_ON]
        assert len(depends_edges) >= 1

    def test_detects_inject(self):
        source = """\
@Singleton
public class OrderService {
    @Inject
    OrderRepository repo;
}
"""
        result = self.detector.detect(_ctx(source))
        inject_nodes = [n for n in result.nodes if "@Inject" in n.annotations]
        assert len(inject_nodes) == 1

    def test_detects_scheduled(self):
        source = """\
@Singleton
public class PollingService {

    @Scheduled(fixedRate = "5m")
    void pollUpdates() {}
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) == 1
        assert events[0].properties["fixed_rate"] == "5m"
        assert events[0].properties["framework"] == "micronaut"

    def test_detects_event_listener(self):
        source = """\
@Singleton
public class StartupListener {

    @EventListener
    void onStartup(ServerStartupEvent event) {}
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT and "@EventListener" in n.annotations]
        assert len(events) == 1

    # --- Negative tests ---

    def test_empty_class_returns_nothing(self):
        result = self.detector.detect(_ctx("public class Foo { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_spring_annotations_not_detected(self):
        source = """\
@RestController
@RequestMapping("/api")
public class SpringController {
    @GetMapping("/hello")
    public String hello() { return "hi"; }
}
"""
        result = self.detector.detect(_ctx(source))
        # No Micronaut-specific nodes should be found
        micronaut_nodes = [n for n in result.nodes if n.properties.get("framework") == "micronaut"]
        assert len(micronaut_nodes) == 0

    def test_plain_java_not_detected(self):
        source = """\
public class MathUtils {
    public static int add(int a, int b) { return a + b; }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    # --- Determinism test ---

    def test_determinism(self):
        source = """\
@Controller("/api/v1")
public class ApiController {

    @Inject
    Repo repo;

    @Get("/items")
    public List<Item> getItems() { return List.of(); }

    @Post("/items")
    public Item createItem(@Body Item item) { return item; }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
