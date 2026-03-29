package io.github.randomcodespace.iq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifies that the Spring application context starts without errors.
 *
 * Neo4j-related beans are excluded via test properties since no Neo4j instance
 * is available during unit tests. The Neo4jConfig class uses conditional
 * annotations to avoid loading repository infrastructure without a driver.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
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
