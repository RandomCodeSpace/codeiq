package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SQLAlchemyModelDetectorTest {

    private final SQLAlchemyModelDetector detector = new SQLAlchemyModelDetector();

    @Test
    void detectsModel() {
        String code = """
                class User(Base):
                    __tablename__ = 'users'
                    id = Column(Integer, primary_key=True)
                    name = Column(String)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        var entityNode = result.nodes().stream().filter(n -> n.getKind() == NodeKind.ENTITY).findFirst().orElseThrow();
        assertEquals("User", entityNode.getLabel());
        assertEquals("users", entityNode.getProperties().get("table_name"));
        assertEquals("sqlalchemy", entityNode.getProperties().get("framework"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) entityNode.getProperties().get("columns");
        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("name"));
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.CONNECTS_TO));
    }

    @Test
    void detectsRelationship() {
        String code = """
                class User(Base):
                    __tablename__ = 'users'
                    id = Column(Integer, primary_key=True)
                    orders = relationship("Order", back_populates="user")
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        assertEquals(2, result.edges().size()); // MAPS_TO + CONNECTS_TO
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.MAPS_TO));
        assertTrue(result.edges().stream().anyMatch(e -> e.getKind() == EdgeKind.CONNECTS_TO));
    }

    @Test
    void defaultTableNameWhenMissing() {
        String code = """
                class Product(Base):
                    id = Column(Integer, primary_key=True)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        var entityNode = result.nodes().stream().filter(n -> n.getKind() == NodeKind.ENTITY).findFirst().orElseThrow();
        assertEquals("products", entityNode.getProperties().get("table_name"));
    }

    @Test
    void noMatchOnPlainClass() {
        String code = """
                class MyService:
                    pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                class User(Base):
                    __tablename__ = 'users'
                    id = Column(Integer, primary_key=True)
                    orders = relationship("Order")

                class Order(Base):
                    __tablename__ = 'orders'
                    id = Column(Integer, primary_key=True)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
