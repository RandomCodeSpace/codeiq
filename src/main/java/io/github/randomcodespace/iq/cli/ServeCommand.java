package io.github.randomcodespace.iq.cli;

import io.github.randomcodespace.iq.cache.AnalysisCache;
import io.github.randomcodespace.iq.config.CodeIqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
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

    @Override
    public Integer call() {
        Path root = path.toAbsolutePath().normalize();

        // Update config with the root path so controllers can find it
        config.setRootPath(root.toString());

        // Check that H2 cache exists and report status
        Path cacheDir;
        if (graphPath != null) {
            cacheDir = graphPath.toAbsolutePath().normalize();
        } else {
            cacheDir = root.resolve(config.getCacheDir());
        }
        Path h2File = cacheDir.resolve("analysis-cache.mv.db");
        if (java.nio.file.Files.exists(h2File)) {
            try (AnalysisCache cache = new AnalysisCache(cacheDir.resolve("analysis-cache.db"))) {
                long nodeCount = cache.getNodeCount();
                long edgeCount = cache.getEdgeCount();
                CliOutput.success("Loaded analysis cache: " + nodeCount + " nodes, " + edgeCount + " edges");
            } catch (Exception e) {
                log.warn("Could not read H2 cache stats", e);
            }
        } else {
            CliOutput.info("No analysis cache found at " + cacheDir);
            CliOutput.info("  Run 'code-iq analyze " + root + "' to populate data.");
        }

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
