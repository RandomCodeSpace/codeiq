package io.github.randomcodespace.iq.detector.jvm.java;

import io.github.randomcodespace.iq.analyzer.DiscoveredFile;
import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.intelligence.resolver.EmptyResolved;
import io.github.randomcodespace.iq.intelligence.resolver.ResolutionException;
import io.github.randomcodespace.iq.intelligence.resolver.Resolved;
import io.github.randomcodespace.iq.intelligence.resolver.java.JavaResolved;
import io.github.randomcodespace.iq.intelligence.resolver.java.JavaSourceRootDiscovery;
import io.github.randomcodespace.iq.intelligence.resolver.java.JavaSymbolResolver;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.Confidence;
import io.github.randomcodespace.iq.model.EdgeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 — ClassHierarchyDetector migration to consume {@code ctx.resolved()}
 * and stamp EXTENDS / IMPLEMENTS edges as RESOLVED with stable FQN targets
 * when the symbol solver can pin them.
 *
 * <p>Class hierarchy resolution is high-leverage: the simple name "Service"
 * appears in dozens of unrelated codebases at once and EXTENDS / IMPLEMENTS
 * edges are downstream-load-bearing for blast-radius / dead-code / cycle
 * analysis. Pinning the FQN turns the edge from "Service-named-something"
 * into "this exact superclass".
 *
 * <p>Three contract tests:
 * <ol>
 *   <li><b>resolvedModeStampsResolvedTierOnExtendsEdge</b> — two
 *       {@code BaseService} classes in different packages; resolution picks
 *       the imported one for the EXTENDS edge.</li>
 *   <li><b>fallbackModeMatchesPreSpecBaseline</b> — EmptyResolved → simple-
 *       name target, no target_fqn, no RESOLVED stamp.</li>
 *   <li><b>mixedModeUsesResolverWhereAvailable</b> — a class that extends a
 *       resolvable type and implements an unresolvable one: EXTENDS is
 *       RESOLVED, IMPLEMENTS falls back.</li>
 * </ol>
 */
class ClassHierarchyDetectorResolvedTest {

    @TempDir Path repoRoot;

    private final ClassHierarchyDetector detector = new ClassHierarchyDetector();
    private JavaSymbolResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example/a"));
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example/b"));
        Files.writeString(repoRoot.resolve("pom.xml"), "<project/>");

        Files.writeString(repoRoot.resolve("src/main/java/com/example/a/BaseService.java"),
                """
                package com.example.a;
                public class BaseService {}
                """);
        Files.writeString(repoRoot.resolve("src/main/java/com/example/b/BaseService.java"),
                """
                package com.example.b;
                public class BaseService {}
                """);
        Files.writeString(repoRoot.resolve("src/main/java/com/example/a/Auditable.java"),
                """
                package com.example.a;
                public interface Auditable {}
                """);

        resolver = new JavaSymbolResolver(new JavaSourceRootDiscovery());
    }

    // ── (1) Resolved mode ────────────────────────────────────────────────────

    @Test
    void resolvedModeStampsResolvedTierOnExtendsEdge() throws Exception {
        // Pet extends BaseService — two BaseService classes in different
        // packages, only the imported one wins.
        String petPath = "src/main/java/com/example/PetService.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import com.example.a.BaseService;
                public class PetService extends BaseService {}
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        assertInstanceOf(JavaResolved.class, resolved);

        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);
        DetectorResult result = detector.detect(ctx);

        CodeEdge extendsEdge = onlyEdge(result, EdgeKind.EXTENDS);
        assertEquals("com.example.a.BaseService", extendsEdge.getProperties().get("target_fqn"));
        assertEquals(Confidence.RESOLVED, extendsEdge.getConfidence());
        assertEquals(detector.getName(), extendsEdge.getSource());
    }

    @Test
    void resolvedModeStampsResolvedTierOnImplementsEdge() throws Exception {
        String petPath = "src/main/java/com/example/PetService.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import com.example.a.Auditable;
                public class PetService implements Auditable {}
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);
        DetectorResult result = detector.detect(ctx);

        CodeEdge implementsEdge = onlyEdge(result, EdgeKind.IMPLEMENTS);
        assertEquals("com.example.a.Auditable", implementsEdge.getProperties().get("target_fqn"));
        assertEquals(Confidence.RESOLVED, implementsEdge.getConfidence());
    }

    // ── (2) Fallback mode ────────────────────────────────────────────────────

    @Test
    void fallbackModeMatchesPreSpecBaseline() throws Exception {
        String petPath = "src/main/java/com/example/PetService.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import com.example.a.BaseService;
                public class PetService extends BaseService {}
                """;
        Files.writeString(absPet, content);

        DetectorContext ctx = ctxFor(petPath, content).withResolved(EmptyResolved.INSTANCE);
        DetectorResult result = detector.detect(ctx);

        CodeEdge extendsEdge = onlyEdge(result, EdgeKind.EXTENDS);
        assertNull(extendsEdge.getProperties().get("target_fqn"),
                "EmptyResolved → no FQN attempt, no target_fqn");
        assertNotEquals(Confidence.RESOLVED, extendsEdge.getConfidence());
        assertNull(extendsEdge.getSource());
    }

    @Test
    void fallbackModeWhenContextHasNoResolvedAtAll() throws Exception {
        String petPath = "src/main/java/com/example/PetService.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                public class PetService extends BaseService {}
                """;
        Files.writeString(absPet, content);

        DetectorContext ctx = ctxFor(petPath, content);
        DetectorResult result = detector.detect(ctx);

        CodeEdge extendsEdge = onlyEdge(result, EdgeKind.EXTENDS);
        assertNull(extendsEdge.getProperties().get("target_fqn"));
        assertNotEquals(Confidence.RESOLVED, extendsEdge.getConfidence());
    }

    // ── (3) Mixed mode ───────────────────────────────────────────────────────

    @Test
    void mixedModeFallsBackForUnreachableType() throws Exception {
        // Class extends a known type and implements an unknown one.
        // Expect: EXTENDS edge gets RESOLVED, IMPLEMENTS edge falls back.
        String petPath = "src/main/java/com/example/PetService.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import com.example.a.BaseService;
                public class PetService extends BaseService implements MysteryAware {}
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);
        DetectorResult result = detector.detect(ctx);

        CodeEdge extendsEdge = onlyEdge(result, EdgeKind.EXTENDS);
        assertEquals("com.example.a.BaseService", extendsEdge.getProperties().get("target_fqn"));
        assertEquals(Confidence.RESOLVED, extendsEdge.getConfidence());

        CodeEdge implementsEdge = onlyEdge(result, EdgeKind.IMPLEMENTS);
        assertNull(implementsEdge.getProperties().get("target_fqn"),
                "MysteryAware has no source — solver fails — fallback");
        assertNotEquals(Confidence.RESOLVED, implementsEdge.getConfidence());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Resolved bootstrapAndResolve(String relPath, String content) throws ResolutionException {
        resolver.bootstrap(repoRoot);
        DiscoveredFile file = new DiscoveredFile(Path.of(relPath), "java", content.length());
        return resolver.resolve(file, content);
    }

    private DetectorContext ctxFor(String relPath, String content) {
        return new DetectorContext(relPath, "java", content, null, null);
    }

    private static CodeEdge onlyEdge(DetectorResult result, EdgeKind kind) {
        List<CodeEdge> matching = result.edges().stream()
                .filter(e -> e.getKind() == kind)
                .toList();
        assertEquals(1, matching.size(),
                "expected exactly one " + kind + " edge, got " + matching.size());
        return matching.get(0);
    }
}
