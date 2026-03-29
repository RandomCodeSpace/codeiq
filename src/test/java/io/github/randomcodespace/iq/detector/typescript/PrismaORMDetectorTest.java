package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

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
    void noMatchOnNonPrismaCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "const p = new PrismaClient();\nprisma.user.findMany();";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
