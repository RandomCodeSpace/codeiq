package io.github.randomcodespace.iq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.randomcodespace.iq.graph.GraphRepository;
import io.github.randomcodespace.iq.graph.GraphStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that the Spring application context starts without errors.
 *
 * Neo4j embedded and related auto-configuration are disabled via properties
 * since no Neo4j instance is available during unit tests. We mock GraphRepository
 * and GraphStore so that beans depending on them (QueryService, GraphController,
 * McpTools, GraphHealthIndicator) can be created.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "codeiq.neo4j.enabled=false",
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.neo4j.autoconfigure.Neo4jAutoConfiguration," +
                        "org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jAutoConfiguration," +
                        "org.springframework.boot.data.neo4j.autoconfigure.DataNeo4jRepositoriesAutoConfiguration"
        }
)
@ActiveProfiles("indexing")
class CodeIqApplicationTest {

    @MockitoBean
    private GraphRepository graphRepository;

    @MockitoBean
    private GraphStore graphStore;

    @Configuration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without errors.
    }
}
