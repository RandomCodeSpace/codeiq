"""Tests for JPA entity detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.java.jpa_entity import JpaEntityDetector
from code_intelligence.models.graph import NodeKind, EdgeKind


def _ctx(content: str, path: str = "User.java", language: str = "java") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestJpaEntityDetector:
    def setup_method(self):
        self.detector = JpaEntityDetector()

    def test_detects_entity_with_table(self):
        source = """\
@Entity
@Table(name = "users")
public class User {

    @Column(name = "user_name")
    private String username;

    @Column(name = "email_address")
    private String email;
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        entity = entities[0]
        assert entity.properties["table_name"] == "users"
        assert "@Entity" in entity.annotations

    def test_detects_entity_without_table(self):
        source = """\
@Entity
public class Product {
    private Long id;
    private String name;
}
"""
        result = self.detector.detect(_ctx(source))
        entities = [n for n in result.nodes if n.kind == NodeKind.ENTITY]
        assert len(entities) == 1
        assert entities[0].properties["table_name"] == "product"

    def test_detects_columns(self):
        source = """\
@Entity
@Table(name = "orders")
public class Order {

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;
}
"""
        result = self.detector.detect(_ctx(source))
        entity = result.nodes[0]
        columns = entity.properties.get("columns", [])
        assert len(columns) >= 2
        col_names = [c["name"] for c in columns]
        assert "order_id" in col_names
        assert "total_amount" in col_names

    def test_detects_relationships(self):
        source = """\
@Entity
public class Order {

    @ManyToOne
    private Customer customer;

    @OneToMany(mappedBy = "order")
    private List<OrderItem> items;
}
"""
        result = self.detector.detect(_ctx(source))
        edges = [e for e in result.edges if e.kind == EdgeKind.MAPS_TO]
        assert len(edges) >= 2
        targets = {e.target.split(":")[-1] for e in edges}
        assert "Customer" in targets
        assert "OrderItem" in targets

    def test_detects_relationship_with_target_entity(self):
        source = """\
@Entity
public class Department {

    @OneToMany(targetEntity = Employee.class, mappedBy = "department")
    private List<Employee> employees;
}
"""
        result = self.detector.detect(_ctx(source))
        edges = [e for e in result.edges if e.kind == EdgeKind.MAPS_TO]
        assert len(edges) >= 1
        assert "Employee" in edges[0].target

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("public class PlainClass { }"))
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_no_entity_annotation(self):
        source = """\
public class NotAnEntity {
    private String name;
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@Entity
@Table(name = "accounts")
public class Account {

    @Column(name = "account_number")
    private String accountNumber;

    @ManyToOne
    private User owner;
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
        assert len(r1.edges) == len(r2.edges)
        assert [e.source for e in r1.edges] == [e.source for e in r2.edges]
