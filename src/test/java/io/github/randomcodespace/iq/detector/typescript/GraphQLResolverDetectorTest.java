package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsNestJSResolverWithEntity() {
        String code = """
                @Resolver(Post)
                export class PostResolver {
                    @Query(() => [Post])
                    async posts() {}
                    @Mutation(() => Post)
                    async deletePost() {}
                    @Subscription()
                    async postAdded() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/post.resolver.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertTrue(result.nodes().size() >= 4);
        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.CLASS && "PostResolver".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().contains("Query"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().contains("Mutation"));
        assertThat(result.nodes()).anyMatch(n -> n.getLabel().contains("Subscription"));
    }

    @Test
    void detectsSchemaDefinedQueryResolvers() {
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
        assertThat(result.nodes()).allMatch(n -> n.getKind() == NodeKind.ENDPOINT);
        assertThat(result.nodes()).allMatch(n -> "GraphQL".equals(n.getProperties().get("protocol")));
    }

    @Test
    void detectsSchemaDefinedMutationResolvers() {
        String code = """
                type Mutation {
                    createUser(name: String!): User
                    updateUser(id: ID!, name: String): User
                    deleteUser(id: ID!): Boolean
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/schema.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "GraphQL Mutation: createUser".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "GraphQL Mutation: deleteUser".equals(n.getLabel()));
        assertThat(result.nodes()).allMatch(n -> "mutation".equals(n.getProperties().get("operation_type")));
    }

    @Test
    void detectsSubscriptionResolvers() {
        String code = """
                type Subscription {
                    messageAdded: Message
                    userJoined: User
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/subscriptions.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(2, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "GraphQL Subscription: messageAdded".equals(n.getLabel()));
    }

    @Test
    void resolverAnnotationHasNestjsGraphqlFramework() {
        String code = """
                @Resolver(of => User)
                export class UserResolver {
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.resolver.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("nestjs-graphql", result.nodes().get(0).getProperties().get("framework"));
        assertThat(result.nodes().get(0).getAnnotations()).contains("@Resolver");
    }

    @Test
    void noMatchOnNonGraphQLCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noMatchOnPlainTypeDefinition() {
        // 'type' keyword without Query/Mutation/Subscription body should not produce endpoints
        String code = """
                type User = {
                    id: string;
                    name: string;
                };
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noEdgesReturned() {
        String code = "type Query { users: [User] }";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "type Query { users: [User] }\ntype Mutation { createUser: User }";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.graphql_resolvers", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
