package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

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
    void noMatchOnNonRemixCode() {
        String code = "const x = 42;";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
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
}
