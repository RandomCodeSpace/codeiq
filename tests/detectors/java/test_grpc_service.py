"""Tests for gRPC service detector."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.detectors.java.grpc_service import GrpcServiceDetector
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "UserServiceImpl.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestGrpcServiceDetector:
    def setup_method(self):
        self.detector = GrpcServiceDetector()

    def test_detects_grpc_service_impl(self):
        source = """\
@GrpcService
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {

    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> observer) {
        observer.onNext(GetUserResponse.newBuilder().build());
        observer.onCompleted();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 1
        # Should detect the service
        service_nodes = [n for n in endpoints if n.properties.get("protocol") == "grpc"]
        assert len(service_nodes) >= 1
        assert any("UserService" in n.label for n in service_nodes)

    def test_detects_grpc_rpc_methods(self):
        source = """\
public class OrderServiceImpl extends OrderServiceGrpc.OrderServiceImplBase {

    @Override
    public void createOrder(CreateOrderRequest request, StreamObserver<CreateOrderResponse> observer) {
        observer.onCompleted();
    }

    @Override
    public void getOrder(GetOrderRequest request, StreamObserver<GetOrderResponse> observer) {
        observer.onCompleted();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        # At least the service + 2 RPC methods
        assert len(endpoints) >= 3
        expose_edges = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(expose_edges) >= 1

    def test_detects_grpc_client_stub(self):
        source = """\
public class OrderClient {
    private OrderServiceGrpc.OrderServiceBlockingStub stub;

    public OrderClient(ManagedChannel channel) {
        this.stub = OrderServiceGrpc.newBlockingStub(channel);
    }
}
"""
        result = self.detector.detect(_ctx(source))
        call_edges = [e for e in result.edges if e.kind == EdgeKind.CALLS]
        assert len(call_edges) >= 1
        assert "OrderService" in call_edges[0].target

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class PlainService { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_grpc_patterns(self):
        source = """\
public class UserService {
    public User getUser(Long id) { return repo.findById(id); }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_determinism(self):
        source = """\
@GrpcService
public class PaymentServiceImpl extends PaymentServiceGrpc.PaymentServiceImplBase {

    @Override
    public void processPayment(PaymentRequest req, StreamObserver<PaymentResponse> obs) {
        obs.onCompleted();
    }
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
