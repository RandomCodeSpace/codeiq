package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PrismaORMDetectorTest {

    private final PrismaORMDetector detector = new PrismaORMDetector();

    @Test
    void detectsPrismaUsage() {
        String code = """
                import { PrismaClient } from '@prisma/client';
                const prisma = new PrismaClient();
                const users = await prisma.user.findMany();
                const post = await prisma.post.create({ data: {} });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // 1 DATABASE_CONNECTION + 2 ENTITY (user, post)
        assertTrue(result.nodes().size() >= 3);
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.DATABASE_CONNECTION));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.ENTITY));
        // Import edge + query edges
        assertTrue(result.edges().size() >= 3);
    }

    @Test
    void detectsPrismaClientConnectionNode() {
        String code = "const prisma = new PrismaClient();";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var connNode = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.DATABASE_CONNECTION)
                .findFirst();
        assertTrue(connNode.isPresent());
        assertEquals("PrismaClient", connNode.get().getLabel());
        assertEquals("prisma", connNode.get().getProperties().get("framework"));
    }

    @Test
    void detectsPrismaImportEdge() {
        String code = "import { PrismaClient } from '@prisma/client';";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.edges().isEmpty());
        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.IMPORTS);
    }

    @Test
    void detectsPrismaRequireImport() {
        String code = "const { PrismaClient } = require('@prisma/client');";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.js", "javascript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.IMPORTS);
    }

    @Test
    void detectsModelEntitiesFromOperations() {
        String code = """
                const prisma = new PrismaClient();
                const allUsers = await prisma.user.findMany();
                const profile = await prisma.profile.findUnique({ where: { id: 1 } });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/repo.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.ENTITY && "user".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.ENTITY && "profile".equals(n.getLabel()));
    }

    @Test
    void detectsMultipleOperationsAsQueryEdges() {
        String code = """
                prisma.user.findMany();
                prisma.user.create({ data: {} });
                prisma.user.delete({ where: { id: 1 } });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/repo.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.QUERIES
                && "findMany".equals(e.getProperties().get("operation")));
        assertThat(result.edges()).anyMatch(e -> "create".equals(e.getProperties().get("operation")));
        assertThat(result.edges()).anyMatch(e -> "delete".equals(e.getProperties().get("operation")));
    }

    @Test
    void detectsTransactionFlag() {
        String code = """
                const prisma = new PrismaClient();
                await prisma.$transaction([
                    prisma.user.create({ data: {} }),
                    prisma.profile.create({ data: {} })
                ]);
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/db.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var connNode = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.DATABASE_CONNECTION)
                .findFirst();
        assertTrue(connNode.isPresent());
        assertEquals(true, connNode.get().getProperties().get("transaction"));
    }

    @Test
    void deduplicatesModelNodes() {
        // Multiple operations on same model should produce only one ENTITY node
        String code = """
                prisma.user.findMany();
                prisma.user.create({ data: {} });
                prisma.user.delete({ where: {} });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/repo.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long userEntityCount = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENTITY && "user".equals(n.getLabel()))
                .count();
        assertEquals(1, userEntityCount);
    }

    @Test
    void noMatchOnNonPrismaCode() {
        String code = "const x = 42;";
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
        String code = """
                const p = new PrismaClient();
                prisma.user.findMany();
                prisma.post.create({ data: {} });
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("prisma_orm", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
