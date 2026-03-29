package io.github.randomcodespace.iq.analyzer;

import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Detects service boundaries by scanning the graph for build file nodes
 * that indicate module boundaries. Runs AFTER all detectors + linkers
 * during the enrich phase.
 * <p>
 * Creates SERVICE nodes and sets the {@code service} property on all
 * child nodes (nodes whose filePath starts with the module directory).
 */
public class ServiceDetector {

    private static final Logger log = LoggerFactory.getLogger(ServiceDetector.class);

    /**
     * Build file patterns that indicate module boundaries.
     * Maps filename to build tool name.
     */
    private static final Map<String, String> BUILD_FILES = Map.of(
            "pom.xml", "maven",
            "package.json", "npm",
            "go.mod", "go",
            "build.gradle", "gradle",
            "build.gradle.kts", "gradle",
            "Cargo.toml", "cargo"
    );

    /** File extension for .csproj files (matched by suffix). */
    private static final String CSPROJ_EXTENSION = ".csproj";

    /**
     * Detect service boundaries from the graph's nodes and create SERVICE nodes.
     *
     * @param nodes      all current nodes in the graph
     * @param edges      all current edges in the graph
     * @param projectDir the project root directory name (used as fallback service name)
     * @return result containing new SERVICE nodes, CONTAINS edges, and
     *         the service property assignments for existing nodes
     */
    public ServiceDetectionResult detect(List<CodeNode> nodes, List<CodeEdge> edges, String projectDir) {
        // 1. Find module boundaries by scanning node file paths for build files
        // Use TreeMap for deterministic ordering (sorted by directory path)
        Map<String, ModuleInfo> modules = new TreeMap<>();

        for (CodeNode node : nodes) {
            String filePath = node.getFilePath();
            if (filePath == null) continue;

            String fileName = Path.of(filePath).getFileName().toString();
            String dirPath = parentDir(filePath);

            // Check known build files
            String buildTool = BUILD_FILES.get(fileName);
            if (buildTool != null) {
                modules.putIfAbsent(dirPath, new ModuleInfo(dirPath, buildTool, fileName));
            }
            // Check .csproj files
            if (fileName.endsWith(CSPROJ_EXTENSION)) {
                modules.putIfAbsent(dirPath, new ModuleInfo(dirPath, "dotnet", fileName));
            }
        }

        // 2. If no modules detected, create one service for the whole project
        if (modules.isEmpty()) {
            modules.put("", new ModuleInfo("", "unknown", ""));
        }

        // 3. Create SERVICE nodes and assign child nodes
        List<CodeNode> serviceNodes = new ArrayList<>();
        List<CodeEdge> serviceEdges = new ArrayList<>();

        // Sort module dirs by length descending so deeper paths match first
        List<String> sortedDirs = new ArrayList<>(modules.keySet());
        sortedDirs.sort((a, b) -> Integer.compare(b.length(), a.length()));

        // Map from module dir -> service node for child assignment
        Map<String, CodeNode> serviceByDir = new LinkedHashMap<>();

        for (var entry : modules.entrySet()) {
            String dir = entry.getKey();
            ModuleInfo info = entry.getValue();

            String serviceName = deriveServiceName(dir, projectDir);

            CodeNode service = new CodeNode();
            service.setId("service:" + serviceName);
            service.setKind(NodeKind.SERVICE);
            service.setLabel(serviceName);
            service.setFilePath(dir.isEmpty() ? "." : dir);
            service.setLayer("backend"); // default, can be refined

            Map<String, Object> props = new LinkedHashMap<>();
            props.put("build_tool", info.buildTool());
            props.put("detected_from", info.buildFile());
            // Counts filled below
            props.put("endpoint_count", 0);
            props.put("entity_count", 0);
            service.setProperties(props);

            serviceNodes.add(service);
            serviceByDir.put(dir, service);
        }

        // 4. Assign service property to all child nodes + count endpoints/entities
        Map<String, Integer> endpointCounts = new LinkedHashMap<>();
        Map<String, Integer> entityCounts = new LinkedHashMap<>();

        for (CodeNode node : nodes) {
            String filePath = node.getFilePath();
            if (filePath == null) filePath = "";

            // Find the best matching service (deepest directory match)
            String matchedDir = null;
            for (String dir : sortedDirs) {
                if (dir.isEmpty() || filePath.startsWith(dir + "/") || filePath.equals(dir)) {
                    matchedDir = dir;
                    break;
                }
            }
            // Fallback to root module if present
            if (matchedDir == null && modules.containsKey("")) {
                matchedDir = "";
            }

            if (matchedDir != null) {
                CodeNode serviceNode = serviceByDir.get(matchedDir);
                if (serviceNode != null) {
                    String serviceName = serviceNode.getLabel();
                    node.getProperties().put("service", serviceName);

                    // Create CONTAINS edge
                    CodeEdge containsEdge = new CodeEdge(
                            "edge:service:" + serviceName + ":contains:" + node.getId(),
                            EdgeKind.CONTAINS,
                            serviceNode.getId(),
                            node
                    );
                    serviceEdges.add(containsEdge);

                    // Count endpoints and entities
                    if (node.getKind() == NodeKind.ENDPOINT) {
                        endpointCounts.merge(serviceName, 1, Integer::sum);
                    } else if (node.getKind() == NodeKind.ENTITY) {
                        entityCounts.merge(serviceName, 1, Integer::sum);
                    }
                }
            }
        }

        // 5. Update counts on service nodes
        for (CodeNode service : serviceNodes) {
            String name = service.getLabel();
            service.getProperties().put("endpoint_count",
                    endpointCounts.getOrDefault(name, 0));
            service.getProperties().put("entity_count",
                    entityCounts.getOrDefault(name, 0));
        }

        log.info("Detected {} service(s): {}", serviceNodes.size(),
                serviceNodes.stream().map(CodeNode::getLabel).toList());

        return new ServiceDetectionResult(serviceNodes, serviceEdges);
    }

    /**
     * Derive a human-readable service name from a directory path.
     */
    private String deriveServiceName(String dir, String projectDir) {
        if (dir.isEmpty()) {
            return projectDir != null && !projectDir.isEmpty() ? projectDir : "root";
        }
        // Use the last path component
        String[] parts = dir.replace('\\', '/').split("/");
        return parts[parts.length - 1];
    }

    /**
     * Get the parent directory of a file path.
     */
    private static String parentDir(String filePath) {
        if (filePath == null) return "";
        String normalized = filePath.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash <= 0) return "";
        return normalized.substring(0, lastSlash);
    }

    /**
     * Internal record for module metadata.
     */
    private record ModuleInfo(String directory, String buildTool, String buildFile) {}

    /**
     * Result of service detection.
     */
    public record ServiceDetectionResult(
            List<CodeNode> serviceNodes,
            List<CodeEdge> serviceEdges
    ) {}
}
