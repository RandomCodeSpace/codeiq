package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class RemixRouteDetectorTest {

    private final RemixRouteDetector detector = new RemixRouteDetector();

    @Test
    void detectsRemixRoutes() {
        String code = """
                export async function loader({ request }: LoaderFunctionArgs) {
                    return json({ users: [] });
                }
                export async function action({ request }: ActionFunctionArgs) {
                    return redirect('/users');
                }
                export default function Users() {
                    const data = useLoaderData();
                    return <div>{data}</div>;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor(
                "app/routes/users.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // loader, action, component
        assertEquals(3, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.ENDPOINT));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.COMPONENT));
        // Route path derived from file path
        assertEquals("/users", result.nodes().get(0).getProperties().get("route_path"));
    }

    @Test
    void detectsLoaderWithHttpGetMethod() {
        String code = "export async function loader({ request }) { return json({}); }";
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/items.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var loader = result.nodes().get(0);
        assertEquals(NodeKind.ENDPOINT, loader.getKind());
        assertEquals("GET", loader.getProperties().get("http_method"));
        assertEquals("loader", loader.getProperties().get("type"));
        assertEquals("remix", loader.getProperties().get("framework"));
    }

    @Test
    void detectsActionWithHttpPostMethod() {
        String code = "export async function action({ request }) { return redirect('/'); }";
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/items.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var action = result.nodes().get(0);
        assertEquals(NodeKind.ENDPOINT, action.getKind());
        assertEquals("POST", action.getProperties().get("http_method"));
        assertEquals("action", action.getProperties().get("type"));
    }

    @Test
    void detectsDefaultComponentExport() {
        String code = """
                export default function ProductPage() {
                    return <div>Products</div>;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/products.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(NodeKind.COMPONENT, result.nodes().get(0).getKind());
        assertEquals("ProductPage", result.nodes().get(0).getLabel());
    }

    @Test
    void derivesRoutePathFromFilePath() {
        String code = "export async function loader() { return json({}); }";
        DetectorContext ctx = DetectorTestUtils.contextFor(
                "app/routes/blog.posts.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("/blog/posts", result.nodes().get(0).getProperties().get("route_path"));
    }

    @Test
    void derivesIndexRouteFromUnderscore() {
        String code = "export async function loader() { return json([]); }";
        DetectorContext ctx = DetectorTestUtils.contextFor(
                "app/routes/_index.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("/", result.nodes().get(0).getProperties().get("route_path"));
    }

    @Test
    void derivesParamRouteFromDollarSign() {
        String code = "export async function loader() { return json({}); }";
        DetectorContext ctx = DetectorTestUtils.contextFor(
                "app/routes/users.$id.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals("/users/:id", result.nodes().get(0).getProperties().get("route_path"));
    }

    @Test
    void noRoutePathWhenNotUnderAppRoutes() {
        String code = "export async function loader() { return json({}); }";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/utils/loader.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertNull(result.nodes().get(0).getProperties().get("route_path"));
    }

    @Test
    void componentWithLoaderDataFlagSet() {
        String code = """
                export default function Index() {
                    const data = useLoaderData();
                    return <div>{data}</div>;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/_index.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(true, result.nodes().get(0).getProperties().get("uses_loader_data"));
    }

    @Test
    void componentWithActionDataFlagSet() {
        String code = """
                export default function Form() {
                    const actionData = useActionData();
                    return <form>{actionData}</form>;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/form.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertEquals(true, result.nodes().get(0).getProperties().get("uses_action_data"));
    }

    @Test
    void noMatchOnNonRemixCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("app/routes/empty.tsx", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "export async function loader() {}\nexport default function Page() {}";
        DetectorContext ctx = DetectorTestUtils.contextFor(
                "app/routes/_index.tsx", "typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("remix_routes", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
