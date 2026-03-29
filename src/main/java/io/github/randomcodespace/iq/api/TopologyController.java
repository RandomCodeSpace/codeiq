package io.github.randomcodespace.iq.api;

import io.github.randomcodespace.iq.cache.AnalysisCache;
import io.github.randomcodespace.iq.config.CodeIqConfig;
import io.github.randomcodespace.iq.model.CodeEdge;
import io.github.randomcodespace.iq.model.CodeNode;
import io.github.randomcodespace.iq.query.TopologyService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for service topology queries.
 */
@RestController
@RequestMapping("/api/topology")
@Profile("serving")
public class TopologyController {

    private final TopologyService topologyService;
    private final CodeIqConfig config;

    public TopologyController(TopologyService topologyService, CodeIqConfig config) {
        this.topologyService = topologyService;
        this.config = config;
    }

    @GetMapping
    public Map<String, Object> getTopology() {
        var data = loadData();
        return topologyService.getTopology(data.nodes(), data.edges());
    }

    @GetMapping("/services/{name}")
    public Map<String, Object> serviceDetail(@PathVariable String name) {
        var data = loadData();
        return topologyService.serviceDetail(name, data.nodes(), data.edges());
    }

    @GetMapping("/services/{name}/deps")
    public Map<String, Object> serviceDependencies(@PathVariable String name) {
        var data = loadData();
        return topologyService.serviceDependencies(name, data.nodes(), data.edges());
    }

    @GetMapping("/services/{name}/dependents")
    public Map<String, Object> serviceDependents(@PathVariable String name) {
        var data = loadData();
        return topologyService.serviceDependents(name, data.nodes(), data.edges());
    }

    @GetMapping("/blast-radius/{nodeId}")
    public Map<String, Object> blastRadius(@PathVariable String nodeId) {
        var data = loadData();
        return topologyService.blastRadius(nodeId, data.nodes(), data.edges());
    }

    @GetMapping("/path")
    public List<Map<String, Object>> findPath(
            @RequestParam("from") String source,
            @RequestParam("to") String target) {
        var data = loadData();
        return topologyService.findPath(source, target, data.nodes(), data.edges());
    }

    @GetMapping("/bottlenecks")
    public List<Map<String, Object>> findBottlenecks() {
        var data = loadData();
        return topologyService.findBottlenecks(data.nodes(), data.edges());
    }

    @GetMapping("/circular")
    public List<List<String>> findCircularDeps() {
        var data = loadData();
        return topologyService.findCircularDeps(data.nodes(), data.edges());
    }

    @GetMapping("/dead")
    public List<Map<String, Object>> findDeadServices() {
        var data = loadData();
        return topologyService.findDeadServices(data.nodes(), data.edges());
    }

    /**
     * Load nodes and edges from the analysis cache.
     */
    private GraphData loadData() {
        Path root = Path.of(config.getRootPath()).toAbsolutePath().normalize();
        Path cachePath = root.resolve(config.getCacheDir()).resolve("analysis-cache.db");
        Path h2File = root.resolve(config.getCacheDir()).resolve("analysis-cache.mv.db");

        if (!Files.exists(h2File)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No analysis cache found. Run analyze first.");
        }

        try (AnalysisCache cache = new AnalysisCache(cachePath)) {
            List<CodeNode> nodes = cache.loadAllNodes();
            List<CodeEdge> edges = cache.loadAllEdges();
            return new GraphData(nodes, edges);
        }
    }

    private record GraphData(List<CodeNode> nodes, List<CodeEdge> edges) {}
}
