"""Tests for Spring application events detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.spring_events import SpringEventsDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "EventHandler.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestSpringEventsDetector:
    def setup_method(self):
        self.detector = SpringEventsDetector()

    def test_detects_event_listener(self):
        source = """\
public class OrderEventHandler {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        notifyWarehouse(event);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) >= 1
        assert any("OrderCreatedEvent" in e.label for e in events)
        listen_edges = [e for e in result.edges if e.kind == EdgeKind.LISTENS]
        assert len(listen_edges) >= 1

    def test_detects_transactional_event_listener(self):
        source = """\
public class AuditHandler {

    @TransactionalEventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        auditService.log(event);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) >= 1
        listen_edges = [e for e in result.edges if e.kind == EdgeKind.LISTENS]
        assert len(listen_edges) >= 1

    def test_detects_publish_event(self):
        source = """\
public class OrderService {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void createOrder(Order order) {
        save(order);
        applicationEventPublisher.publishEvent(new OrderCreatedEvent(order));
    }
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) >= 1
        publish_edges = [e for e in result.edges if e.kind == EdgeKind.PUBLISHES]
        assert len(publish_edges) >= 1

    def test_detects_event_class_definition(self):
        source = """\
public class OrderCreatedEvent extends ApplicationEvent {
    private final Order order;

    public OrderCreatedEvent(Order order) {
        super(order);
        this.order = order;
    }
}
"""
        result = self.detector.detect(_ctx(source))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) >= 1
        assert events[0].properties["event_class"] == "OrderCreatedEvent"

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class PlainService { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_event_patterns(self):
        source = """\
public class UserService {
    public User getUser(Long id) { return repo.findById(id); }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
public class NotificationHandler {

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {}

    public void publishWelcome() {
        applicationEventPublisher.publishEvent(new WelcomeEvent(event.getUser()));
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
