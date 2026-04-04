package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsEntityWithExplicitTableName() {
        String code = """
                @Entity('order_items')
                export class OrderItem {
                    @Column()
                    quantity: number;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/order-item.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var entity = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY)
                .findFirst();
        assertTrue(entity.isPresent());
        assertEquals("order_items", entity.get().getProperties().get("table_name"));
        assertThat(entity.get().getAnnotations()).contains("@Entity");
    }

    @Test
    void detectsEntityWithoutTableNameUsesClassName() {
        // @Entity() without table name: defaults to lowercase class name + 's'
        String code = """
                @Entity()
                export class Product {
                    @Column()
                    name: string;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/product.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var entity = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY)
                .findFirst();
        assertTrue(entity.isPresent());
        assertEquals("Product", entity.get().getLabel());
        // Default table name = className.toLowerCase() + 's'
        assertEquals("products", entity.get().getProperties().get("table_name"));
    }

    @Test
    void extractsColumns() {
        String code = """
                @Entity('products')
                export class Product {
                    @Column()
                    name: string;
                    @Column()
                    price: number;
                    @Column()
                    description: string;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/product.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var entity = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY)
                .findFirst();
        assertTrue(entity.isPresent());
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) entity.get().getProperties().get("columns");
        assertNotNull(columns);
        assertThat(columns).contains("name", "price", "description");
    }

    @Test
    void detectsRelationshipsAsEdges() {
        String code = """
                @Entity('orders')
                export class Order {
                    @ManyToOne(() => User)
                    user: User;
                    @OneToMany(() => OrderItem)
                    items: OrderItem[];
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/order.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.MAPS_TO);
    }

    @Test
    void detectsManyToManyRelationship() {
        String code = """
                @Entity('students')
                export class Student {
                    @ManyToMany(() => Course)
                    courses: Course[];
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/student.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.MAPS_TO);
    }

    @Test
    void detectsOneToOneRelationship() {
        String code = """
                @Entity('profiles')
                export class Profile {
                    @OneToOne(() => User)
                    user: User;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/profile.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.MAPS_TO);
    }

    @Test
    void connectsToDatabaseNode() {
        String code = """
                @Entity('items')
                export class Item {
                    @Column()
                    name: string;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/item.entity.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.CONNECTS_TO);
        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.DATABASE_CONNECTION);
    }

    @Test
    void noMatchOnNonTypeORMCode() {
        String code = "class SomeService {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "@Entity('items')\nexport class Item {\n    @Column()\n    name: string;\n}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.typeorm_entities", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript");
    }
}
