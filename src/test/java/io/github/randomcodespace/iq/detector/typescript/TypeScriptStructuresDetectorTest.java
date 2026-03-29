package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TypeScriptStructuresDetectorTest {

    private final TypeScriptStructuresDetector detector = new TypeScriptStructuresDetector();

    @Test
    void detectsAllStructures() {
        String code = """
                import { Foo } from './foo';
                export interface UserDTO {}
                export type UserId = string;
                export class UserService {}
                export async function getUser() {}
                export const createUser = async () => {};
                export enum UserRole { ADMIN, USER }
                export namespace Users {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // interface, type, class, function, const func, enum, namespace = 7 nodes
        assertEquals(7, result.nodes().size());
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.INTERFACE));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.ENUM));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.METHOD));
        assertTrue(result.nodes().stream().anyMatch(n -> n.getKind() == NodeKind.MODULE));
        // Import edge
        assertEquals(1, result.edges().size());
    }

    @Test
    void noMatchOnEmptyFile() {
        String code = "";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "interface A {}\nclass B {}\nfunction c() {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void avoidsDuplicateConstFunc() {
        String code = """
                export function handler() {}
                export const handler = () => {};
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        // Should only have 1 node for 'handler' (function wins, const is skipped)
        long handlerCount = result.nodes().stream()
                .filter(n -> "handler".equals(n.getLabel()))
                .count();
        assertEquals(1, handlerCount);
    }
}
