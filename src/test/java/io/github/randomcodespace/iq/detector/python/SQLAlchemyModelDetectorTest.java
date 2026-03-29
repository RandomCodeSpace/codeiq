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

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENTITY, result.nodes().get(0).getKind());
        assertEquals("User", result.nodes().get(0).getLabel());
        assertEquals("users", result.nodes().get(0).getProperties().get("table_name"));
        assertEquals("sqlalchemy", result.nodes().get(0).getProperties().get("framework"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) result.nodes().get(0).getProperties().get("columns");
        assertTrue(columns.contains("id"));
        assertTrue(columns.contains("name"));
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

        assertEquals(1, result.nodes().size());
        assertEquals(1, result.edges().size());
        assertEquals(EdgeKind.MAPS_TO, result.edges().get(0).getKind());
    }

    @Test
    void defaultTableNameWhenMissing() {
        String code = """
                class Product(Base):
                    id = Column(Integer, primary_key=True)
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("products", result.nodes().get(0).getProperties().get("table_name"));
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
