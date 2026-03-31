package io.github.randomcodespace.iq.cli;

import io.github.randomcodespace.iq.analyzer.AnalysisResult;
import io.github.randomcodespace.iq.analyzer.Analyzer;
import io.github.randomcodespace.iq.cache.AnalysisCache;
import io.github.randomcodespace.iq.config.CodeIqConfig;
import io.github.randomcodespace.iq.graph.GraphStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Start the web UI + REST API + MCP server.
 *
 * This command signals to the application that it should keep running
 * (the web server is already started by Spring Boot when the "serving" profile
 * is active). The serve command simply prints server info and blocks until
 * the Spring context shuts down.
 *
 * On startup, if the Neo4j graph is empty but an H2 analysis cache exists,
 * the command auto-loads data from H2 into Neo4j (like a mini enrich).
 */
@Component
@Command(name = "serve", mixinStandardHelpOptions = true,
        description = "Start web UI + REST API + MCP server")
public class ServeCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(ServeCommand.class);

    /** Marker flag — checked by CodeIqApplication to activate serving profile. */
    public static final String COMMAND_NAME = "serve";

    @Parameters(index = "0", defaultValue = ".", description = "Path to analyzed codebase")
    private Path path;

    @Option(names = {"--port", "-p"}, defaultValue = "8080", description = "Server port")
    private int port;

    @Option(names = {"--host"}, defaultValue = "0.0.0.0", description = "Bind address")
    private String host;

    @Option(names = {"--graph"}, description = "Path to shared graph directory (overrides default)")
    private Path graphPath;

    @Autowired
    private CodeIqConfig config;

    @Autowired
    private Analyzer analyzer;

    @Autowired(required = false)
    private GraphStore graphStore;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Override
    public Integer call() {
        Path root = path.toAbsolutePath().normalize();

        // Update config with the root path so controllers can find it
        config.setRootPath(root.toString());

        // Auto-enrich: if Neo4j is empty, run full analysis to populate it
        autoEnrich(root);

        CliOutput.step("\uD83D\uDE80", "@|bold,green Server started|@");
        System.out.println();
        CliOutput.info("  URL:       http://" + host + ":" + port);
        CliOutput.info("  REST API:  http://" + host + ":" + port + "/api");
        CliOutput.info("  MCP:       http://" + host + ":" + port + "/mcp");
        CliOutput.info("  Health:    http://" + host + ":" + port + "/actuator/health");
        CliOutput.info("  API Docs:  http://" + host + ":" + port + "/docs");
        CliOutput.info("  Codebase:  " + root);
        System.out.println();
        CliOutput.info("Press Ctrl+C to stop.");

        // The Spring Boot web server is already running. We block here
        // to prevent the CommandLineRunner from returning (which would
        // trigger application shutdown). The JVM shutdown hook will
        // handle cleanup.
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return 0;
    }

    /**
     * Auto-enrich Neo4j on startup: if the graph is empty, run the full analysis
     * pipeline to populate Neo4j from the codebase. This ensures the API/MCP
     * endpoints return data immediately without requiring a manual POST /api/analyze.
     */
    private void autoEnrich(Path root) {
        // Check if Neo4j already has data
        boolean neo4jEmpty = true;
        if (graphStore != null) {
            try {
                neo4jEmpty = graphStore.count() == 0;
            } catch (Exception e) {
                log.debug("Could not check Neo4j state", e);
            }
        }

        if (!neo4jEmpty) {
            long nodeCount = graphStore.count();
            long edgeCount = graphStore.countEdges();
            CliOutput.success("Neo4j graph loaded: " + nodeCount + " nodes, " + edgeCount + " edges");
            return;
        }

        // Neo4j is empty — run analysis to populate it
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
        CliOutput.step("\u26A1", "Auto-enriching Neo4j graph...");

        try {
            AnalysisResult result = analyzer.run(root, null, true, msg -> {
                if (msg.startsWith("Discovering") || msg.startsWith("Found") || msg.startsWith("Analyzing")
                        || msg.startsWith("Building") || msg.startsWith("Linking") || msg.startsWith("Classifying")
                        || msg.startsWith("Analysis complete")) {
                    CliOutput.info("  " + msg);
                }
            });

            // Persist to Neo4j
            if (graphStore != null && result.nodes() != null && !result.nodes().isEmpty()) {
                CliOutput.step("\uD83D\uDCBE", "Persisting " + nf.format(result.nodes().size()) + " nodes to Neo4j...");
                graphStore.bulkSave(result.nodes());
            }

            // Evict Spring caches
            if (cacheManager != null) {
                cacheManager.getCacheNames().forEach(name -> {
                    var cache = cacheManager.getCache(name);
                    if (cache != null) cache.clear();
                });
            }

            CliOutput.success("Graph ready: " + nf.format(result.nodeCount()) + " nodes, "
                    + nf.format(result.edgeCount()) + " edges from "
                    + nf.format(result.filesAnalyzed()) + " files");
        } catch (Exception e) {
            log.error("Auto-enrich failed", e);
            CliOutput.error("Auto-enrich failed: " + e.getMessage());
            CliOutput.info("  You can manually trigger analysis via POST /api/analyze");
        }
    }

    public Path getPath() {
        return path;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public Path getGraphPath() {
        return graphPath;
    }
}
