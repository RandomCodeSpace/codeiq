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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 — JpaEntityDetector migration to consume {@code ctx.resolved()} and
 * promote relationship edges from SYNTACTIC → RESOLVED.
 *
 * <p>Three contract tests per the plan:
 * <ol>
 *   <li><b>resolvedModeProducesResolvedEdge</b> — when ctx.resolved() carries a
 *       {@link JavaResolved}, the relationship edge gets a stable
 *       {@code target_fqn} property and {@link Confidence#RESOLVED}.</li>
 *   <li><b>fallbackModeMatchesPreSpecBaseline</b> — without a resolver the
 *       edge has no {@code target_fqn} and the default-stamping leaves
 *       confidence/source for the orchestrator (matches pre-migration
 *       observable shape).</li>
 *   <li><b>mixedModeUsesResolverWhereAvailable</b> — a single class with a
 *       resolvable {@code @OneToMany List<KnownEntity>} and an unresolvable
 *       {@code @ManyToOne UnknownEntity} produces one RESOLVED edge and one
 *       falling back to default tier.</li>
 * </ol>
 */
class JpaEntityDetectorResolvedTest {

    @TempDir Path repoRoot;

    private final JpaEntityDetector detector = new JpaEntityDetector();
    private JavaSymbolResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        // Maven-shaped layout — JavaSourceRootDiscovery picks src/main/java.
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example/a"));
        Files.createDirectories(repoRoot.resolve("src/main/java/com/example/b"));
        Files.writeString(repoRoot.resolve("pom.xml"), "<project/>");

        // Two Owner classes in different packages — the imported one is the
        // canonical resolution target.
        Files.writeString(repoRoot.resolve("src/main/java/com/example/a/Owner.java"),
                """
                package com.example.a;
                public class Owner {}
                """);
        Files.writeString(repoRoot.resolve("src/main/java/com/example/b/Owner.java"),
                """
                package com.example.b;
                public class Owner {}
                """);

        resolver = new JavaSymbolResolver(new JavaSourceRootDiscovery());
    }

    // ── (1) Resolved mode ────────────────────────────────────────────────────

    @Test
    void resolvedModeProducesResolvedEdgeWithTargetFqn() throws Exception {
        // Pet imports com.example.a.Owner and uses it in @ManyToOne.
        String petPath = "src/main/java/com/example/Pet.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import javax.persistence.*;
                import com.example.a.Owner;
                @Entity
                @Table(name = "pet")
                public class Pet {
                    @Id private Long id;
                    @ManyToOne private Owner owner;
                }
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        assertInstanceOf(JavaResolved.class, resolved,
                "resolver must return JavaResolved for a valid Java source file");

        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);

        DetectorResult result = detector.detect(ctx);

        CodeEdge mapsTo = onlyMapsToEdge(result);
        assertEquals("com.example.a.Owner", mapsTo.getProperties().get("target_fqn"),
                "resolved tier must pin the imported package's Owner FQN, not the b/ Owner");
        assertEquals(Confidence.RESOLVED, mapsTo.getConfidence(),
                "edge with a resolved target_fqn is RESOLVED tier");
        assertEquals(detector.getName(), mapsTo.getSource(),
                "detector explicitly stamps source on RESOLVED edges");
    }

    @Test
    void resolvedModeFindsCollectionGenericArg() throws Exception {
        // @OneToMany List<Owner> — generic arg [0] is the relationship target.
        String petPath = "src/main/java/com/example/PetOwner.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import javax.persistence.*;
                import java.util.List;
                import com.example.a.Owner;
                @Entity
                @Table(name = "pet_owner")
                public class PetOwner {
                    @Id private Long id;
                    @OneToMany private List<Owner> owners;
                }
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);

        DetectorResult result = detector.detect(ctx);

        CodeEdge mapsTo = onlyMapsToEdge(result);
        assertEquals("com.example.a.Owner", mapsTo.getProperties().get("target_fqn"));
        assertEquals(Confidence.RESOLVED, mapsTo.getConfidence());
    }

    // ── (2) Fallback mode ────────────────────────────────────────────────────

    @Test
    void fallbackModeMatchesPreSpecBaseline() throws Exception {
        // ctx.resolved() is EmptyResolved → no resolution attempts → no
        // target_fqn property; confidence/source left for orchestrator
        // defaulting (matches pre-migration shape).
        String petPath = "src/main/java/com/example/Pet.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import javax.persistence.*;
                import com.example.a.Owner;
                @Entity
                public class Pet {
                    @Id private Long id;
                    @ManyToOne private Owner owner;
                }
                """;
        Files.writeString(absPet, content);

        DetectorContext ctx = ctxFor(petPath, content).withResolved(EmptyResolved.INSTANCE);

        DetectorResult result = detector.detect(ctx);

        CodeEdge mapsTo = onlyMapsToEdge(result);
        assertNull(mapsTo.getProperties().get("target_fqn"),
                "fallback mode must not synthesise a target_fqn — resolver was unavailable");
        assertNull(mapsTo.getSource(),
                "detector leaves source null in fallback mode (orchestrator stamps default)");
        // Confidence default also left unstamped — orchestrator's
        // DetectorEmissionDefaults applies SYNTACTIC at the analyzer boundary.
        assertEquals(Confidence.LEXICAL, mapsTo.getConfidence(),
                "raw edge default before orchestrator stamping is LEXICAL");
    }

    @Test
    void fallbackModeWhenContextHasNoResolvedAtAll() throws Exception {
        // Same shape as above but ctx.resolved() is Optional.empty() — older
        // call path that never threaded a Resolved through. Still must work.
        String petPath = "src/main/java/com/example/Pet.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import javax.persistence.*;
                import com.example.a.Owner;
                @Entity
                public class Pet {
                    @Id private Long id;
                    @ManyToOne private Owner owner;
                }
                """;
        Files.writeString(absPet, content);

        DetectorContext ctx = ctxFor(petPath, content);
        // No withResolved call — Optional.empty() default.

        DetectorResult result = detector.detect(ctx);
        CodeEdge mapsTo = onlyMapsToEdge(result);
        assertNull(mapsTo.getProperties().get("target_fqn"));
    }

    // ── (3) Mixed mode ───────────────────────────────────────────────────────

    @Test
    void mixedModeUsesResolverWhereAvailable() throws Exception {
        // Pet has two relationships — one resolvable (Owner from com.example.a),
        // one unresolvable (Vet — class doesn't exist in any source root).
        // Expect: Owner edge gets RESOLVED + target_fqn; Vet edge falls back.
        String petPath = "src/main/java/com/example/Pet.java";
        Path absPet = repoRoot.resolve(petPath);
        Files.createDirectories(absPet.getParent());
        String content = """
                package com.example;
                import javax.persistence.*;
                import com.example.a.Owner;
                @Entity
                public class Pet {
                    @Id private Long id;
                    @ManyToOne private Owner owner;
                    @ManyToOne private Vet vet;
                }
                """;
        Files.writeString(absPet, content);

        Resolved resolved = bootstrapAndResolve(petPath, content);
        DetectorContext ctx = ctxFor(petPath, content).withResolved(resolved);

        DetectorResult result = detector.detect(ctx);

        List<CodeEdge> mapsTo = result.edges().stream()
                .filter(e -> e.getKind() == EdgeKind.MAPS_TO)
                .toList();
        assertEquals(2, mapsTo.size(), "two relationships → two MAPS_TO edges");

        CodeEdge ownerEdge = mapsTo.stream()
                .filter(e -> "Owner".equals(e.getTarget().getLabel()))
                .findFirst().orElseThrow();
        CodeEdge vetEdge = mapsTo.stream()
                .filter(e -> "Vet".equals(e.getTarget().getLabel()))
                .findFirst().orElseThrow();

        assertEquals("com.example.a.Owner", ownerEdge.getProperties().get("target_fqn"));
        assertEquals(Confidence.RESOLVED, ownerEdge.getConfidence());

        assertNull(vetEdge.getProperties().get("target_fqn"),
                "Vet has no source on the project — resolver returns nothing");
        // Vet edge confidence is the raw enum default (LEXICAL); orchestrator
        // would stamp SYNTACTIC if this went through Analyzer. Either way:
        // not RESOLVED.
        assertNotEquals(Confidence.RESOLVED, vetEdge.getConfidence());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Bootstrap the resolver against the synthetic repo and resolve a single
     * file's content into a {@link Resolved}. Keeps the per-test setup terse.
     */
    private Resolved bootstrapAndResolve(String relPath, String content) throws ResolutionException {
        resolver.bootstrap(repoRoot);
        DiscoveredFile file = new DiscoveredFile(Path.of(relPath), "java", content.length());
        return resolver.resolve(file, content);
    }

    /** Build a vanilla DetectorContext with our synthetic file path + content. */
    private DetectorContext ctxFor(String relPath, String content) {
        return new DetectorContext(relPath, "java", content, null, null);
    }

    /**
     * Pull the single MAPS_TO edge out of the result. Matches the contract the
     * detector promises for our single-relationship fixtures; fails loudly if
     * the count is unexpected so test breakage points to a real shape change.
     */
    private static CodeEdge onlyMapsToEdge(DetectorResult result) {
        List<CodeEdge> mapsTo = result.edges().stream()
                .filter(e -> e.getKind() == EdgeKind.MAPS_TO)
                .toList();
        assertEquals(1, mapsTo.size(),
                "expected exactly one MAPS_TO edge; got " + mapsTo.size()
                        + " — detector shape changed?");
        return mapsTo.get(0);
    }

    @SuppressWarnings("unused") // Imported for future test additions.
    private static <T> Optional<T> opt(T value) { return Optional.ofNullable(value); }
}
