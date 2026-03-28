"""Tests for Java detectors."""

from osscodeiq.detectors.base import DetectorContext, DetectorResult
from osscodeiq.models.graph import NodeKind, EdgeKind


def _ctx(content: bytes, language: str = "java", file_path: str = "Test.java") -> DetectorContext:
    return DetectorContext(
        file_path=file_path,
        language=language,
        content=content,
        module_name="test-module",
    )


class TestSpringRestDetector:
    def test_detect_get_mapping(self, order_controller_source):
        from osscodeiq.detectors.java.spring_rest import SpringRestDetector
        detector = SpringRestDetector()
        result = detector.detect(_ctx(order_controller_source, file_path="OrderController.java"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 2  # At least GET and POST
        methods = {n.properties.get("http_method") for n in endpoints}
        assert "GET" in methods or "get" in {m.lower() for m in methods if m}

    def test_supported_languages(self):
        from osscodeiq.detectors.java.spring_rest import SpringRestDetector
        d = SpringRestDetector()
        assert "java" in d.supported_languages


class TestJpaEntityDetector:
    def test_detect_entity(self, order_entity_source):
        from osscodeiq.detectors.java.jpa_entity import JpaEntityDetector
        detector = JpaEntityDetector()
        result = detector.detect(_ctx(order_entity_source, file_path="Order.java"))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) >= 1
        order_entity = entities[0]
        assert "order" in order_entity.label.lower() or "order" in order_entity.properties.get("table_name", "").lower()


class TestRepositoryDetector:
    def test_detect_repository(self, order_repository_source):
        from osscodeiq.detectors.java.repository import RepositoryDetector
        detector = RepositoryDetector()
        result = detector.detect(_ctx(order_repository_source, file_path="OrderRepository.java"))
        repos = [n for n in result.nodes if n.kind == NodeKind.REPOSITORY]
        assert len(repos) >= 1


class TestKafkaDetector:
    def test_detect_kafka(self, order_event_handler_source):
        from osscodeiq.detectors.java.kafka import KafkaDetector
        detector = KafkaDetector()
        result = detector.detect(_ctx(order_event_handler_source, file_path="OrderEventHandler.java"))
        # Should detect topics and consumer/producer patterns
        topics = [n for n in result.nodes if n.kind == NodeKind.TOPIC]
        assert len(topics) >= 1
        # Should have consume edges
        consume_edges = [e for e in result.edges if e.kind == EdgeKind.CONSUMES]
        assert len(consume_edges) >= 1


class TestSpringEventsDetector:
    def test_detect_events(self, order_event_handler_source):
        from osscodeiq.detectors.java.spring_events import SpringEventsDetector
        detector = SpringEventsDetector()
        result = detector.detect(_ctx(order_event_handler_source, file_path="OrderEventHandler.java"))
        events = [n for n in result.nodes if n.kind == NodeKind.EVENT]
        assert len(events) >= 1


class TestModuleDepsDetector:
    def test_detect_pom_modules(self, pom_xml_source):
        from osscodeiq.detectors.java.module_deps import ModuleDepsDetector
        detector = ModuleDepsDetector()
        result = detector.detect(_ctx(pom_xml_source, language="xml", file_path="pom.xml"))
        modules = [n for n in result.nodes if n.kind == NodeKind.MODULE]
        assert len(modules) >= 1


# ---------------------------------------------------------------------------
# Tree-sitter helpers for ClassHierarchyDetector tests
# ---------------------------------------------------------------------------

import tree_sitter
import tree_sitter_java
from pathlib import Path

_JAVA_FIXTURES = Path(__file__).resolve().parent.parent / "fixtures" / "java"


def _ctx_with_tree(content: bytes, file_path: str = "Test.java") -> DetectorContext:
    parser = tree_sitter.Parser(tree_sitter.Language(tree_sitter_java.language()))
    tree = parser.parse(content)
    return DetectorContext(
        file_path=file_path,
        language="java",
        content=content,
        tree=tree,
        module_name="test-module",
    )


class TestClassHierarchyDetector:
    def test_detect_interface(self):
        from osscodeiq.detectors.java.class_hierarchy import ClassHierarchyDetector

        source = _JAVA_FIXTURES.joinpath("Serializer.java").read_bytes()
        ctx = _ctx_with_tree(source, file_path="Serializer.java")
        detector = ClassHierarchyDetector()
        result = detector.detect(ctx)

        interfaces = [n for n in result.nodes if n.kind == NodeKind.INTERFACE]
        assert len(interfaces) == 1
        assert interfaces[0].label == "Serializer"
        # Should extend Closeable
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) == 1
        assert extends_edges[0].target == "*:Closeable"

    def test_detect_class_implements(self):
        from osscodeiq.detectors.java.class_hierarchy import ClassHierarchyDetector

        source = _JAVA_FIXTURES.joinpath("StringSerializer.java").read_bytes()
        ctx = _ctx_with_tree(source, file_path="StringSerializer.java")
        detector = ClassHierarchyDetector()
        result = detector.detect(ctx)

        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert classes[0].label == "StringSerializer"
        impl_edges = [e for e in result.edges if e.kind == EdgeKind.IMPLEMENTS]
        assert len(impl_edges) == 1
        assert impl_edges[0].target == "*:Serializer"

    def test_detect_enum(self):
        from osscodeiq.detectors.java.class_hierarchy import ClassHierarchyDetector

        source = _JAVA_FIXTURES.joinpath("ApiKeys.java").read_bytes()
        ctx = _ctx_with_tree(source, file_path="ApiKeys.java")
        detector = ClassHierarchyDetector()
        result = detector.detect(ctx)

        enums = [n for n in result.nodes if n.kind == NodeKind.ENUM]
        assert len(enums) == 1
        assert enums[0].label == "ApiKeys"
        assert enums[0].properties["visibility"] == "public"

    def test_no_tree_returns_empty(self):
        from osscodeiq.detectors.java.class_hierarchy import ClassHierarchyDetector

        ctx = DetectorContext(
            file_path="Empty.java",
            language="java",
            content=b"",
            tree=None,
            module_name="test-module",
        )
        detector = ClassHierarchyDetector()
        result = detector.detect(ctx)
        assert len(result.nodes) == 0
        assert len(result.edges) == 0


# ---------------------------------------------------------------------------
# PublicApiDetector tests
# ---------------------------------------------------------------------------
class TestPublicApiDetector:
    def test_detect_public_methods(self, order_controller_source):
        from osscodeiq.detectors.java.public_api import PublicApiDetector

        detector = PublicApiDetector()
        ctx = _ctx_with_tree(order_controller_source, file_path="OrderController.java")
        result = detector.detect(ctx)

        method_nodes = [n for n in result.nodes if n.kind == NodeKind.METHOD]
        method_names = {n.label.split(".")[-1] for n in method_nodes}

        # OrderController has: listOrders, getOrder, createOrder, updateOrder, deleteOrder
        assert "listOrders" in method_names
        assert "createOrder" in method_names
        assert "deleteOrder" in method_names

        # Every node must have a DEFINES edge
        define_edges = [e for e in result.edges if e.kind == EdgeKind.DEFINES]
        assert len(define_edges) == len(method_nodes)

    def test_skip_private_methods(self):
        from osscodeiq.detectors.java.public_api import PublicApiDetector

        source = b"""\
public class Foo {
    public void doPublic() {
        System.out.println("public");
    }
    private void doPrivate() {
        System.out.println("private");
    }
    void packagePrivate() {
        System.out.println("pp");
    }
    protected void doProtected() {
        System.out.println("protected");
    }
}
"""
        detector = PublicApiDetector()
        result = detector.detect(_ctx_with_tree(source))

        method_names = {n.label.split(".")[-1] for n in result.nodes}
        assert "doPublic" in method_names
        assert "doProtected" in method_names
        assert "doPrivate" not in method_names
        assert "packagePrivate" not in method_names

    def test_method_id_uniqueness(self):
        from osscodeiq.detectors.java.public_api import PublicApiDetector

        source = b"""\
public class Bar {
    public void foo(String s) {
        System.out.println(s);
    }
    public void foo(int n) {
        System.out.println(n);
    }
}
"""
        detector = PublicApiDetector()
        result = detector.detect(_ctx_with_tree(source))

        ids = [n.id for n in result.nodes]
        assert len(ids) == 2
        assert ids[0] != ids[1]
        # Verify the parameter signatures show up in the IDs
        assert "String" in ids[0] or "String" in ids[1]
        assert "int" in ids[0] or "int" in ids[1]

    def test_no_tree_returns_empty(self):
        from osscodeiq.detectors.java.public_api import PublicApiDetector

        detector = PublicApiDetector()
        ctx = DetectorContext(
            file_path="Empty.java",
            language="java",
            content=b"",
            tree=None,
            module_name="test-module",
        )
        result = detector.detect(ctx)
        assert result.nodes == []
        assert result.edges == []


class TestKafkaProtocolDetector:
    def test_detect_request(self, fetch_request_source):
        from osscodeiq.detectors.java.kafka_protocol import KafkaProtocolDetector

        detector = KafkaProtocolDetector()
        result = detector.detect(_ctx(fetch_request_source, file_path="FetchRequest.java"))
        msgs = [n for n in result.nodes if n.kind == NodeKind.PROTOCOL_MESSAGE]
        assert len(msgs) == 1
        assert msgs[0].properties["protocol_type"] == "request"
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) == 1
        assert extends_edges[0].target == "*:AbstractRequest"

    def test_detect_response(self, fetch_response_source):
        from osscodeiq.detectors.java.kafka_protocol import KafkaProtocolDetector

        detector = KafkaProtocolDetector()
        result = detector.detect(_ctx(fetch_response_source, file_path="FetchResponse.java"))
        msgs = [n for n in result.nodes if n.kind == NodeKind.PROTOCOL_MESSAGE]
        assert len(msgs) == 1
        assert msgs[0].properties["protocol_type"] == "response"
        extends_edges = [e for e in result.edges if e.kind == EdgeKind.EXTENDS]
        assert len(extends_edges) == 1
        assert extends_edges[0].target == "*:AbstractResponse"

    def test_fast_bail(self):
        from osscodeiq.detectors.java.kafka_protocol import KafkaProtocolDetector

        detector = KafkaProtocolDetector()
        source = b"public class Foo extends Bar { }"
        result = detector.detect(_ctx(source, file_path="Foo.java"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0


class TestJaxrsDetector:
    def test_detect_jaxrs_endpoints(self, connectors_resource_source):
        from osscodeiq.detectors.java.jaxrs import JaxrsDetector
        detector = JaxrsDetector()
        result = detector.detect(_ctx(connectors_resource_source, file_path="ConnectorsResource.java"))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 4
        # Verify HTTP methods present
        methods = {n.properties.get("http_method") for n in endpoints}
        assert "GET" in methods
        assert "POST" in methods
        assert "DELETE" in methods
        # Verify paths include class-level base path /connectors
        paths = {n.properties.get("path") for n in endpoints}
        assert "/connectors" in paths
        assert "/connectors/{connector}" in paths
        # Verify edges
        expose_edges = [e for e in result.edges if e.kind == EdgeKind.EXPOSES]
        assert len(expose_edges) >= 4

    def test_jaxrs_fast_bail(self):
        from osscodeiq.detectors.java.jaxrs import JaxrsDetector
        detector = JaxrsDetector()
        content = b"public class Foo { public void bar() {} }"
        result = detector.detect(_ctx(content, file_path="Foo.java"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_supported_languages(self):
        from osscodeiq.detectors.java.jaxrs import JaxrsDetector
        d = JaxrsDetector()
        assert "java" in d.supported_languages


# ---------------------------------------------------------------------------
# ConfigDefDetector tests
# ---------------------------------------------------------------------------
class TestConfigDefDetector:
    def test_detect_config_keys(self, consumer_config_source):
        from osscodeiq.detectors.java.config_def import ConfigDefDetector

        detector = ConfigDefDetector()
        result = detector.detect(_ctx(consumer_config_source, file_path="ConsumerConfig.java"))

        config_nodes = [n for n in result.nodes if n.kind == NodeKind.CONFIG_DEFINITION]
        config_keys = {n.properties["config_key"] for n in config_nodes}

        # The regex catches inline string literals: "group.id" and "auto.offset.reset"
        assert len(config_nodes) >= 2
        assert "group.id" in config_keys
        assert "auto.offset.reset" in config_keys

    def test_reads_config_edges(self, consumer_config_source):
        from osscodeiq.detectors.java.config_def import ConfigDefDetector

        detector = ConfigDefDetector()
        result = detector.detect(_ctx(consumer_config_source, file_path="ConsumerConfig.java"))

        reads_edges = [e for e in result.edges if e.kind == EdgeKind.READS_CONFIG]
        assert len(reads_edges) >= 2

        # All edges should originate from the class
        for edge in reads_edges:
            assert edge.source == "ConsumerConfig.java:ConsumerConfig"
            assert edge.target.startswith("config:")

    def test_fast_bail(self):
        from osscodeiq.detectors.java.config_def import ConfigDefDetector

        source = b"""\
public class PlainService {
    private String name;
    public void doWork() {}
}
"""
        detector = ConfigDefDetector()
        result = detector.detect(_ctx(source))
        assert result.nodes == []
        assert result.edges == []


class TestJdbcDetector:
    def test_detect_jdbc_template(self):
        source = b'''
        import org.springframework.jdbc.core.JdbcTemplate;
        public class UserDao {
            private final JdbcTemplate jdbcTemplate;
            public UserDao(JdbcTemplate jdbcTemplate) {
                this.jdbcTemplate = jdbcTemplate;
            }
        }
        '''
        from osscodeiq.detectors.java.jdbc import JdbcDetector
        detector = JdbcDetector()
        result = detector.detect(_ctx(source, file_path="UserDao.java"))
        db_nodes = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(db_nodes) >= 1

    def test_detect_driver_manager(self):
        source = b'''
        public class DbConnect {
            public void connect() {
                Connection conn = DriverManager.getConnection("jdbc:postgresql://localhost:5432/mydb");
            }
        }
        '''
        from osscodeiq.detectors.java.jdbc import JdbcDetector
        detector = JdbcDetector()
        result = detector.detect(_ctx(source, file_path="DbConnect.java"))
        db_nodes = [n for n in result.nodes if n.kind == NodeKind.DATABASE_CONNECTION]
        assert len(db_nodes) >= 1
        assert any("postgresql" in n.properties.get("db_type", "") for n in db_nodes)

    def test_fast_bail(self):
        from osscodeiq.detectors.java.jdbc import JdbcDetector
        result = JdbcDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0


class TestIbmMqDetector:
    def test_detect_queue_manager(self):
        source = b'''
        import com.ibm.mq.MQQueueManager;
        public class MqClient {
            MQQueueManager qm = new MQQueueManager("QM_PRODUCTION");
        }
        '''
        from osscodeiq.detectors.java.ibm_mq import IbmMqDetector
        result = IbmMqDetector().detect(_ctx(source, file_path="MqClient.java"))
        assert len(result.nodes) >= 1
        assert any("QM_PRODUCTION" in n.label for n in result.nodes)

    def test_fast_bail(self):
        from osscodeiq.detectors.java.ibm_mq import IbmMqDetector
        result = IbmMqDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0


class TestTibcoEmsDetector:
    def test_detect_ems(self):
        source = b'''
        import com.tibco.tibjms.TibjmsConnectionFactory;
        public class EmsPublisher {
            TibjmsConnectionFactory factory = new TibjmsConnectionFactory("tcp://ems-server:7222");
        }
        '''
        from osscodeiq.detectors.java.tibco_ems import TibcoEmsDetector
        result = TibcoEmsDetector().detect(_ctx(source, file_path="EmsPublisher.java"))
        assert len(result.nodes) >= 1

    def test_fast_bail(self):
        from osscodeiq.detectors.java.tibco_ems import TibcoEmsDetector
        result = TibcoEmsDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0


class TestAzureMessagingDetector:
    def test_detect_service_bus(self):
        source = b'''
        import com.azure.messaging.servicebus.ServiceBusSenderClient;
        public class OrderPublisher {
            ServiceBusSenderClient sender;
            public void send() {
                sender.sendMessage(new ServiceBusMessage("data"));
            }
        }
        '''
        from osscodeiq.detectors.java.azure_messaging import AzureMessagingDetector
        result = AzureMessagingDetector().detect(_ctx(source, file_path="OrderPublisher.java"))
        assert len(result.nodes) >= 1

    def test_detect_event_hub(self):
        source = b'''
        import com.azure.messaging.eventhubs.EventHubProducerClient;
        public class EventPublisher {
            EventHubProducerClient producer;
        }
        '''
        from osscodeiq.detectors.java.azure_messaging import AzureMessagingDetector
        result = AzureMessagingDetector().detect(_ctx(source, file_path="EventPublisher.java"))
        assert len(result.nodes) >= 1

    def test_fast_bail(self):
        from osscodeiq.detectors.java.azure_messaging import AzureMessagingDetector
        result = AzureMessagingDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0


class TestAzureFunctionsDetector:
    def test_detect_http_trigger(self):
        source = b'''
        import com.microsoft.azure.functions.annotation.*;
        public class HttpFunction {
            @FunctionName("getUsers")
            public HttpResponseMessage run(
                @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request) {
                return request.createResponseBuilder(HttpStatus.OK).build();
            }
        }
        '''
        from osscodeiq.detectors.java.azure_functions import AzureFunctionsDetector
        result = AzureFunctionsDetector().detect(_ctx(source, file_path="HttpFunction.java"))
        funcs = [n for n in result.nodes if n.kind == NodeKind.AZURE_FUNCTION]
        assert len(funcs) >= 1
        assert any("getUsers" in n.label for n in funcs)

    def test_detect_servicebus_trigger(self):
        source = b'''
        public class QueueProcessor {
            @FunctionName("processOrder")
            public void run(
                @ServiceBusQueueTrigger(name = "msg", queueName = "orders-queue", connection = "SB_CONN")
                String message) {}
        }
        '''
        from osscodeiq.detectors.java.azure_functions import AzureFunctionsDetector
        result = AzureFunctionsDetector().detect(_ctx(source, file_path="QueueProcessor.java"))
        funcs = [n for n in result.nodes if n.kind == NodeKind.AZURE_FUNCTION]
        assert len(funcs) >= 1

    def test_detect_ts_functions(self):
        source = b'''
        const { app } = require("@azure/functions");
        app.http("getUsers", { methods: ["GET"], handler: async (req, ctx) => {} });
        app.serviceBusQueue("processOrder", { queueName: "orders", handler: async (msg, ctx) => {} });
        '''
        from osscodeiq.detectors.java.azure_functions import AzureFunctionsDetector
        ctx = DetectorContext(file_path="functions.ts", language="typescript", content=source, module_name="api")
        result = AzureFunctionsDetector().detect(ctx)
        funcs = [n for n in result.nodes if n.kind == NodeKind.AZURE_FUNCTION]
        assert len(funcs) >= 2

    def test_fast_bail(self):
        from osscodeiq.detectors.java.azure_functions import AzureFunctionsDetector
        result = AzureFunctionsDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0


class TestCosmosDbDetector:
    def test_detect_cosmos_client(self):
        source = b'''
        import com.azure.cosmos.CosmosClient;
        public class UserRepository {
            CosmosClient client;
            public void query() {
                client.getDatabase("mydb").getContainer("users");
            }
        }
        '''
        from osscodeiq.detectors.java.cosmos_db import CosmosDbDetector
        result = CosmosDbDetector().detect(_ctx(source, file_path="UserRepository.java"))
        cosmos_nodes = [n for n in result.nodes if n.kind == NodeKind.AZURE_RESOURCE]
        assert len(cosmos_nodes) >= 1

    def test_fast_bail(self):
        from osscodeiq.detectors.java.cosmos_db import CosmosDbDetector
        result = CosmosDbDetector().detect(_ctx(b"public class Foo {}", file_path="Foo.java"))
        assert len(result.nodes) == 0
