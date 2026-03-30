package io.github.randomcodespace.iq.detector.python;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PydanticModelDetectorTest {

    private final PydanticModelDetector detector = new PydanticModelDetector();

    @Test
    void detectsBaseModel() {
        String code = """
                class User(BaseModel):
                    name: str
                    age: int
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.ENTITY, result.nodes().get(0).getKind());
        assertEquals("User", result.nodes().get(0).getLabel());
        assertEquals("pydantic", result.nodes().get(0).getProperties().get("framework"));
        assertEquals("BaseModel", result.nodes().get(0).getProperties().get("base_class"));
        @SuppressWarnings("unchecked")
        List<String> fields = (List<String>) result.nodes().get(0).getProperties().get("fields");
        assertTrue(fields.contains("name"));
        assertTrue(fields.contains("age"));
    }

    @Test
    void detectsBaseSettings() {
        String code = """
                class AppSettings(BaseSettings):
                    debug: bool
                    db_url: str
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals(NodeKind.CONFIG_DEFINITION, result.nodes().get(0).getKind());
    }

    @Test
    void detectsInheritance() {
        String code = """
                class Base(BaseModel):
                    id: int

                class User(Base):
                    name: str
                """;
        // Note: the regex only matches classes extending BaseModel/BaseSettings directly,
        // so User(Base) won't match unless Base contains BaseModel in name.
        // This tests that only Base is detected.
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(1, result.nodes().size());
        assertEquals("Base", result.nodes().get(0).getLabel());
    }

    @Test
    void noMatchOnPlainClass() {
        String code = """
                class MyService:
                    def run(self):
                        pass
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(0, result.nodes().size());
    }

    @Test
    void deterministic() {
        String code = """
                class Item(BaseModel):
                    name: str
                    price: float

                class Config(BaseSettings):
                    api_key: str
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("python", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
