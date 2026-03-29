package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GraphQLResolverDetectorTest {

    private final GraphQLResolverDetector detector = new GraphQLResolverDetector();

    @Test
    void detectsNestJSResolvers() {
        String code = """
                @Resolver(of => User)
                export class UserResolver {
                    @Query()
                    async getUsers() {}
                    @Mutation()
                    async createUser() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.resolver.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertTrue(result.nodes().size() >= 3);
        // Class node
        assertEquals(NodeKind.CLASS, result.nodes().get(0).getKind());
        assertEquals("UserResolver", result.nodes().get(0).getLabel());
        // Query endpoint
        assertEquals(NodeKind.ENDPOINT, result.nodes().get(1).getKind());
        assertEquals("GraphQL", result.nodes().get(1).getProperties().get("protocol"));
    }

    @Test
    void detectsSchemaDefinedResolvers() {
        String code = """
                type Query {
                    users: [User]
                    user(id: ID!): User
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertEquals("GraphQL Query: users", result.nodes().get(0).getLabel());
    }

    @Test
    void noMatchOnNonGraphQLCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "type Query { users: [User] }";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }
}
