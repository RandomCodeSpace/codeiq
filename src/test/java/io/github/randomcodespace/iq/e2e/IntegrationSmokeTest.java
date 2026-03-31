package io.github.randomcodespace.iq.e2e;

import io.github.randomcodespace.iq.analyzer.AnalysisResult;
import io.github.randomcodespace.iq.analyzer.Analyzer;
import io.github.randomcodespace.iq.analyzer.FileDiscovery;
import io.github.randomcodespace.iq.analyzer.LayerClassifier;
import io.github.randomcodespace.iq.analyzer.StructuredParser;
import io.github.randomcodespace.iq.analyzer.linker.EntityLinker;
import io.github.randomcodespace.iq.analyzer.linker.Linker;
import io.github.randomcodespace.iq.analyzer.linker.ModuleContainmentLinker;
import io.github.randomcodespace.iq.analyzer.linker.TopicLinker;
import io.github.randomcodespace.iq.config.CodeIqConfig;
import io.github.randomcodespace.iq.detector.Detector;
import io.github.randomcodespace.iq.detector.DetectorRegistry;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration smoke tests that validate the analysis pipeline against
 * diverse real-world test repositories. Verifies no crashes, reasonable
 * node/edge counts, correct language detection, and basic sanity.
 *
 * <p>Enabled when {@code INTEGRATION_TEST_DIR} env var points to a directory
 * containing cloned test repos (terraform-vpc, kit, fastapi, kafka, nest, spring-boot).
 *
 * <pre>
 * INTEGRATION_TEST_DIR=/home/sandbox/projects/testDir \
 *   mvn test -Dtest="IntegrationSmokeTest" -pl .
 * </pre>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST_DIR", matches = ".+")
class IntegrationSmokeTest {

    private static Analyzer buildAnalyzer() {
        List<Detector> detectors = discoverDetectors();
        var registry = new DetectorRegistry(detectors);
        var parser = new StructuredParser();
        var config = new CodeIqConfig();
        var fileDiscovery = new FileDiscovery(config);
        var layerClassifier = new LayerClassifier();
        List<Linker> linkers = List.of(
                new EntityLinker(),
                new ModuleContainmentLinker(),
                new TopicLinker()
        );
        return new Analyzer(registry, parser, fileDiscovery, layerClassifier, linkers, config);
    }

    @SuppressWarnings("unchecked")
    private static List<Detector> discoverDetectors() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Detector.class));
        List<Detector> detectors = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("io.github.randomcodespace.iq.detector")) {
            try {
                Class<? extends Detector> clazz = (Class<? extends Detector>) Class.forName(bd.getBeanClassName());
                if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    detectors.add(clazz.getDeclaredConstructor().newInstance());
                }
            } catch (Exception e) {
                // Skip detectors that can't be instantiated
            }
        }
        return detectors;
    }

    private Path repoDir(String name) {
        Path base = Path.of(System.getenv("INTEGRATION_TEST_DIR"));
        Path repo = base.resolve(name);
        assertTrue(Files.isDirectory(repo), "Test repo not found: " + repo);
        return repo;
    }

    private AnalysisResult analyze(String repoName) {
        Analyzer analyzer = buildAnalyzer();
        return analyzer.run(repoDir(repoName), null);
    }

    // --- Small: terraform-vpc (HCL) ---

    @Test
    void terraformVpc_producesNodes() {
        AnalysisResult result = analyze("terraform-vpc");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 5,
                "terraform-vpc should produce at least 5 nodes, got " + result.nodeCount());

        // Should detect node kinds
        assertNotNull(result.nodes());
        Set<NodeKind> kinds = result.nodes().stream()
                .map(CodeNode::getKind)
                .collect(Collectors.toSet());
        assertFalse(kinds.isEmpty(), "Should detect multiple node kinds");
    }

    // --- Medium: kit (Go) ---

    @Test
    void kit_producesGoNodes() {
        AnalysisResult result = analyze("kit");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 50,
                "kit (Go) should produce at least 50 nodes, got " + result.nodeCount());

        // Should detect Go via language breakdown
        assertNotNull(result.languageBreakdown());
        assertTrue(result.languageBreakdown().containsKey("go"),
                "kit should detect Go language, found: " + result.languageBreakdown().keySet());
    }

    // --- Medium: fastapi (Python) ---

    @Test
    void fastapi_producesPythonNodes() {
        AnalysisResult result = analyze("fastapi");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 100,
                "fastapi should produce at least 100 nodes, got " + result.nodeCount());

        assertNotNull(result.languageBreakdown());
        assertTrue(result.languageBreakdown().containsKey("python"),
                "fastapi should detect Python, found: " + result.languageBreakdown().keySet());
    }

    // --- Large: kafka (Java/Scala) ---

    @Test
    void kafka_producesJavaNodes() {
        AnalysisResult result = analyze("kafka");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 500,
                "kafka should produce at least 500 nodes, got " + result.nodeCount());

        assertNotNull(result.languageBreakdown());
        boolean hasJavaOrScala = result.languageBreakdown().containsKey("java")
                || result.languageBreakdown().containsKey("scala");
        assertTrue(hasJavaOrScala,
                "kafka should detect Java or Scala, found: " + result.languageBreakdown().keySet());
    }

    // --- Large: nest (TypeScript) ---

    @Test
    void nest_producesTypeScriptNodes() {
        AnalysisResult result = analyze("nest");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 200,
                "nest should produce at least 200 nodes, got " + result.nodeCount());

        assertNotNull(result.languageBreakdown());
        assertTrue(result.languageBreakdown().containsKey("typescript"),
                "nest should detect TypeScript, found: " + result.languageBreakdown().keySet());
    }

    // --- Monorepo: spring-boot (Java) ---

    @Test
    void springBoot_producesLargeGraph() {
        AnalysisResult result = analyze("spring-boot");
        assertNotNull(result);
        assertTrue(result.nodeCount() >= 1000,
                "spring-boot monorepo should produce at least 1000 nodes, got " + result.nodeCount());

        // Should have edges
        assertTrue(result.edgeCount() > 0,
                "spring-boot should produce edges");

        // Should detect Java
        assertNotNull(result.languageBreakdown());
        assertTrue(result.languageBreakdown().containsKey("java"),
                "spring-boot should detect Java, found: " + result.languageBreakdown().keySet());
    }

    // --- Cross-cutting: edge sanity ---

    @Test
    void smallRepos_edgesReferenceValidNodes() {
        for (String repo : List.of("terraform-vpc", "kit")) {
            AnalysisResult result = analyze(repo);
            assertNotNull(result.nodes(), repo + " should return nodes list");

            Set<String> nodeIds = result.nodes().stream()
                    .map(CodeNode::getId)
                    .collect(Collectors.toSet());

            // Verify edges on nodes reference valid targets
            for (CodeNode node : result.nodes()) {
                for (var edge : node.getEdges()) {
                    assertTrue(nodeIds.contains(edge.getSourceId()),
                            repo + ": Edge source " + edge.getSourceId() + " not found in nodes");
                    if (edge.getTarget() != null) {
                        assertTrue(nodeIds.contains(edge.getTarget().getId()),
                                repo + ": Edge target " + edge.getTarget().getId() + " not found in nodes");
                    }
                }
            }
        }
    }

    // --- Determinism ---

    @Test
    void terraformVpc_deterministicOutput() {
        AnalysisResult first = analyze("terraform-vpc");
        AnalysisResult second = analyze("terraform-vpc");

        assertEquals(first.nodeCount(), second.nodeCount(),
                "Node count should be deterministic");
        assertEquals(first.edgeCount(), second.edgeCount(),
                "Edge count should be deterministic");

        assertNotNull(first.nodes());
        assertNotNull(second.nodes());
        List<String> firstIds = first.nodes().stream()
                .map(CodeNode::getId).sorted().toList();
        List<String> secondIds = second.nodes().stream()
                .map(CodeNode::getId).sorted().toList();
        assertEquals(firstIds, secondIds,
                "Node IDs should be identical across runs");
    }
}
