package io.github.randomcodespace.iq.config;

import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.GraphDatabaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

import java.nio.file.Path;

/**
 * Neo4j Embedded configuration.
 *
 * Configures a file-based Neo4j embedded instance using {@link DatabaseManagementService}.
 * No Bolt driver is needed — the database runs in-process.
 *
 * Disabled when {@code codeiq.neo4j.enabled} is explicitly set to {@code false}
 * (e.g. in tests that do not need an embedded database).
 */
@Configuration
@ConditionalOnProperty(name = "codeiq.neo4j.enabled", havingValue = "true", matchIfMissing = true)
@EnableNeo4jRepositories(basePackages = "io.github.randomcodespace.iq.graph")
public class Neo4jConfig {

    @Bean(destroyMethod = "shutdown")
    DatabaseManagementService databaseManagementService(
            @Value("${codeiq.graph.path:.osscodeiq/graph.db}") String dbPath) {
        DatabaseManagementService dbms = new DatabaseManagementServiceBuilder(Path.of(dbPath)).build();
        // Ensure clean shutdown even if Spring context is not closed gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                dbms.shutdown();
            } catch (Exception ignored) {
                // Already shut down by Spring's destroyMethod, or JVM is exiting
            }
        }, "neo4j-shutdown-hook"));
        return dbms;
    }

    @Bean
    GraphDatabaseService graphDatabaseService(DatabaseManagementService dbms) {
        return dbms.database("neo4j");
    }
}
