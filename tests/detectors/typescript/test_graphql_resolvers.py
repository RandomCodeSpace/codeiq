"""Tests for GraphQL resolver detector."""

from code_intelligence.detectors.base import DetectorContext, DetectorResult
from code_intelligence.detectors.typescript.graphql_resolvers import GraphQLResolverDetector
from code_intelligence.models.graph import NodeKind


def _ctx(content: str, path: str = "user.resolver.ts", language: str = "typescript") -> DetectorContext:
    return DetectorContext(
        file_path=path, language=language, content=content.encode(), module_name="test"
    )


class TestGraphQLResolverDetector:
    def setup_method(self):
        self.detector = GraphQLResolverDetector()

    def test_detects_nestjs_resolver(self):
        source = """\
@Resolver(of => User)
export class UserResolver {

    @Query()
    users() {
        return this.userService.findAll();
    }

    @Mutation()
    createUser() {
        return this.userService.create();
    }
}
"""
        result = self.detector.detect(_ctx(source))
        classes = [n for n in result.nodes if n.kind == NodeKind.CLASS]
        assert len(classes) == 1
        assert classes[0].label == "UserResolver"
        assert "@Resolver" in classes[0].annotations

        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 2
        ops = {n.properties["operation_type"] for n in endpoints}
        assert "query" in ops
        assert "mutation" in ops

    def test_detects_subscription(self):
        source = """\
@Resolver()
export class NotificationResolver {

    @Subscription()
    onNotification() {
        return pubSub.asyncIterator('notifications');
    }
}
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) == 1
        assert endpoints[0].properties["operation_type"] == "subscription"

    def test_detects_schema_defined_types(self):
        source = """\
const typeDefs = gql`
type Query {
    users: [User]
    user(id: ID!): User
}
type Mutation {
    createUser(input: CreateUserInput!): User
}
`;
"""
        result = self.detector.detect(_ctx(source))
        endpoints = [n for n in result.nodes if n.kind == NodeKind.ENDPOINT]
        assert len(endpoints) >= 3
        field_names = {n.properties["field_name"] for n in endpoints}
        assert "users" in field_names
        assert "user" in field_names
        assert "createUser" in field_names

    def test_empty_returns_nothing(self):
        result = self.detector.detect(_ctx("const x = 1;\n"))
        assert len(result.nodes) == 0

    def test_no_graphql_patterns(self):
        source = """\
export class PlainService {
    doWork() { return 'done'; }
}
"""
        result = self.detector.detect(_ctx(source))
        assert len(result.nodes) == 0

    def test_determinism(self):
        source = """\
@Resolver(of => Post)
export class PostResolver {
    @Query()
    posts() {}
    @Mutation()
    createPost() {}
}
"""
        r1 = self.detector.detect(_ctx(source))
        r2 = self.detector.detect(_ctx(source))
        assert len(r1.nodes) == len(r2.nodes)
        assert [n.id for n in r1.nodes] == [n.id for n in r2.nodes]
