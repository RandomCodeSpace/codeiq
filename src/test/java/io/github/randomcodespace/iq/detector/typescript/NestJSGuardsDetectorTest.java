package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NestJSGuardsDetectorTest {

    private final NestJSGuardsDetector detector = new NestJSGuardsDetector();

    @Test
    void detectsGuardsAndRoles() {
        String code = """
                import { UseGuards } from '@nestjs/common';
                import { AuthGuard } from '@nestjs/passport';
                @UseGuards(JwtAuthGuard, RolesGuard)
                @Roles('admin', 'user')
                canActivate(context) {
                    return true;
                }
                AuthGuard('jwt')
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.guard.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // 2 UseGuards + 1 Roles + 1 canActivate + 1 AuthGuard = 5
        assertEquals(5, result.nodes().size());
        assertTrue(result.nodes().stream().allMatch(n -> n.getKind() == NodeKind.GUARD));
        assertTrue(result.nodes().stream().anyMatch(n ->
                "UseGuards(JwtAuthGuard)".equals(n.getLabel())));
        assertTrue(result.nodes().stream().anyMatch(n ->
                n.getLabel().contains("Roles(admin, user)")));
    }

    @Test
    void detectsUseGuardsWithSingleGuard() {
        String code = """
                import { UseGuards } from '@nestjs/common';
                @UseGuards(JwtAuthGuard)
                class UsersController {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/users.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var guard = result.nodes().get(0);
        assertEquals(NodeKind.GUARD, guard.getKind());
        assertEquals("UseGuards(JwtAuthGuard)", guard.getLabel());
        assertEquals("nestjs_guard", guard.getProperties().get("auth_type"));
        assertEquals("JwtAuthGuard", guard.getProperties().get("guard_name"));
        assertThat(guard.getAnnotations()).contains("@UseGuards");
    }

    @Test
    void detectsUseGuardsWithMultipleGuards() {
        String code = """
                import { UseGuards } from '@nestjs/common';
                @UseGuards(AuthGuard, RolesGuard, ThrottleGuard)
                class AppController {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.nodes().size());
        assertThat(result.nodes()).anyMatch(n -> "UseGuards(AuthGuard)".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "UseGuards(RolesGuard)".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "UseGuards(ThrottleGuard)".equals(n.getLabel()));
    }

    @Test
    void detectsRolesDecorator() {
        String code = """
                import { UseGuards } from '@nestjs/common';
                @Roles('admin', 'super-admin')
                @UseGuards(RolesGuard)
                class AdminController {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/admin.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var rolesNode = result.nodes().stream()
                .filter(n -> n.getLabel().startsWith("Roles("))
                .findFirst();
        assertTrue(rolesNode.isPresent());
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) rolesNode.get().getProperties().get("roles");
        assertThat(roles).contains("admin", "super-admin");
        assertThat(rolesNode.get().getAnnotations()).contains("@Roles");
    }

    @Test
    void detectsCanActivateImplementation() {
        String code = """
                import { CanActivate } from '@nestjs/common';
                export class CustomGuard implements CanActivate {
                    async canActivate(context: ExecutionContext) {
                        return this.authService.validate(context);
                    }
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/custom.guard.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        var canActivateNode = result.nodes().stream()
                .filter(n -> "canActivate()".equals(n.getLabel()))
                .findFirst();
        assertTrue(canActivateNode.isPresent());
        assertEquals("canActivate", canActivateNode.get().getProperties().get("guard_impl"));
    }

    @Test
    void detectsAuthGuardStrategy() {
        String code = """
                import { UseGuards } from '@nestjs/common';
                @UseGuards(AuthGuard('jwt'))
                class JwtController {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/jwt.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var authGuardNode = result.nodes().stream()
                .filter(n -> n.getLabel().startsWith("AuthGuard("))
                .findFirst();
        assertTrue(authGuardNode.isPresent());
        assertEquals("jwt", authGuardNode.get().getProperties().get("strategy"));
    }

    @Test
    void noMatchWithoutNestJSImport() {
        // Generic TypeScript with canActivate() but no @nestjs import
        String code = """
                class RouteGuard {
                    canActivate(context) {
                        return this.authService.isAuthenticated();
                    }
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/route.guard.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noMatchOnNonGuardCode() {
        String code = "class SomeService {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noFalsePositiveOnGenericCanActivate() {
        // canActivate() in Angular or custom code without @nestjs/ import must not match
        String code = """
                import { CanActivate } from '@angular/router';
                export class AuthGuard implements CanActivate {
                    canActivate(route, state) { return true; }
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.guard.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty(), "Should not match Angular guard without @nestjs/ import");
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noEdgesReturned() {
        String code = "import { UseGuards } from '@nestjs/common';\n@UseGuards(AuthGuard)\ncanActivate() {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.guard.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "import { UseGuards } from '@nestjs/common';\n@UseGuards(AuthGuard)\n@Roles('admin')";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/auth.guard.ts", "typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.nestjs_guards", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript");
    }
}
