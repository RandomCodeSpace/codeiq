package io.github.randomcodespace.iq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context starts without errors.
 *
 * Neo4j embedded and related auto-configuration are disabled via properties
 * since no Neo4j instance is available during unit tests.
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

    @Test
    void contextLoads() {
        // Verifies that the Spring application context starts without errors.
    }
}
