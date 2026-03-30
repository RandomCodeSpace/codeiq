package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeORMEntityDetectorTest {

    private final TypeORMEntityDetector detector = new TypeORMEntityDetector();

    @Test
    void detectsTypeORMEntities() {
        String code = """
                @Entity('users')
                export class User {
                    @Column()
                    name: string;
                    @Column()
                    email: string;
                    @ManyToOne(() => Department)
                    department: Department;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size()); // entity + database:unknown
        var entityNode = result.nodes().stream().filter(n -> n.getKind() == NodeKind.ENTITY).findFirst().orElseThrow();
        assertEquals("User", entityNode.getLabel());
        assertEquals("users", entityNode.getProperties().get("table_name"));
        assertEquals("typeorm", entityNode.getProperties().get("framework"));
        // Relationship edge + CONNECTS_TO edge
        assertEquals(2, result.edges().size());
    }

    @Test
    void noMatchOnNonTypeORMCode() {
        String code = "class SomeService {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "@Entity()\nexport class Item {\n    @Column()\n    name: string;\n}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
