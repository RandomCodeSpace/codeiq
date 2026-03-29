package io.github.randomcodespace.iq.config;

import org.neo4j.driver.Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j configuration.
 *
 * Spring Data Neo4j auto-configuration handles driver setup via application.yml.
 * This class enables repository scanning only when a Neo4j Driver bean is available,
 * allowing the application context to start without Neo4j for testing.
 */
@Configuration
@ConditionalOnBean(Driver.class)
@EnableNeo4jRepositories(basePackages = "io.github.randomcodespace.iq.graph")
public class Neo4jConfig {
}
