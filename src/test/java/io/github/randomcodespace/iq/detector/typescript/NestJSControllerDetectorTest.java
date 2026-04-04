package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class NestJSControllerDetectorTest {

    private final NestJSControllerDetector detector = new NestJSControllerDetector();

    @Test
    void detectsNestJSController() {
        String code = """
                import { Controller, Get, Post } from '@nestjs/common';
                @Controller('users')
                export class UsersController {
                    @Get()
                    findAll() {}
                    @Post()
                    create() {}
                    @Get('/:id')
                    findOne() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/users.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        // 1 class + 3 endpoints
        assertEquals(4, result.nodes().size());
        assertEquals(NodeKind.CLASS, result.nodes().get(0).getKind());
        assertEquals("nestjs", result.nodes().get(0).getProperties().get("framework"));
        // Endpoints
        assertTrue(result.nodes().stream().anyMatch(n ->
                n.getKind() == NodeKind.ENDPOINT && "GET /users".equals(n.getLabel())));
        // EXPOSES edges with valid targets
        assertEquals(3, result.edges().size());
        assertTrue(result.edges().stream().allMatch(e ->
                e.getKind() == EdgeKind.EXPOSES && e.getTarget() != null));
    }

    @Test
    void detectsControllerClassNode() {
        String code = """
                import { Controller, Get } from '@nestjs/common';
                @Controller('products')
                export class ProductsController {
                    @Get()
                    findAll() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/products.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var classNode = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.CLASS)
                .findFirst();
        assertTrue(classNode.isPresent());
        assertEquals("ProductsController", classNode.get().getLabel());
        assertThat(classNode.get().getAnnotations()).contains("@Controller");
        assertEquals("controller", classNode.get().getProperties().get("stereotype"));
    }

    @Test
    void buildsFullPathFromControllerAndRoute() {
        String code = """
                import { Controller, Get, Post } from '@nestjs/common';
                @Controller('api/v1/users')
                export class UsersController {
                    @Get('/:id')
                    findOne() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/users.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n ->
                n.getKind() == NodeKind.ENDPOINT && "GET /api/v1/users/:id".equals(n.getLabel()));
    }

    @Test
    void detectsAllHttpMethodDecorators() {
        String code = """
                import { Controller, Get, Post, Put, Delete, Patch } from '@nestjs/common';
                @Controller('items')
                export class ItemsController {
                    @Get()
                    list() {}
                    @Post()
                    create() {}
                    @Put('/:id')
                    update() {}
                    @Delete('/:id')
                    remove() {}
                    @Patch('/:id')
                    patch() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/items.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> "GET /items".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "POST /items".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "PUT /items/:id".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "DELETE /items/:id".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "PATCH /items/:id".equals(n.getLabel()));
    }

    @Test
    void endpointHasRestProtocol() {
        String code = """
                import { Controller, Get } from '@nestjs/common';
                @Controller('data')
                export class DataController {
                    @Get()
                    getData() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/data.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        var endpoint = result.nodes().stream()
                .filter(n -> n.getKind() == NodeKind.ENDPOINT)
                .findFirst();
        assertTrue(endpoint.isPresent());
        assertEquals("REST", endpoint.get().getProperties().get("protocol"));
        assertEquals("nestjs", endpoint.get().getProperties().get("framework"));
    }

    @Test
    void detectsHttpClientCallEdge() {
        String code = """
                import { Controller, Get } from '@nestjs/common';
                @Controller('proxy')
                export class ProxyController {
                    @Get('/external')
                    async getExternal() {
                        return this.httpService.get('https://api.example.com/data');
                    }
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/proxy.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.edges()).anyMatch(e -> e.getKind() == EdgeKind.CALLS);
    }

    @Test
    void noMatchWithoutNestJSImport() {
        // Generic TypeScript with @Controller-like patterns but no @nestjs import
        String code = """
                @Controller('users')
                export class UsersController {
                    @Get()
                    findAll() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/users.controller.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
        assertTrue(result.edges().isEmpty());
    }

    @Test
    void noMatchOnAngularComponent() {
        // Angular also uses @Component decorator, should not match NestJS
        String code = """
                import { Component } from '@angular/core';
                @Component({ selector: 'app-root', templateUrl: './app.component.html' })
                export class AppComponent {
                    @Get('/users')
                    getUsers() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.component.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noMatchOnNonNestJSCode() {
        String code = "class SomeService {}";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void noFalsePositiveOnAngularController() {
        // Angular @Controller-like patterns without @nestjs/ import must not match
        String code = """
                import { Component } from '@angular/core';
                @Controller('items')
                export class ItemsComponent {
                    @Get()
                    list() {}
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/items.component.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty(), "Should not match Angular component without @nestjs/ import");
    }

    @Test
    void emptyContentReturnsEmpty() {
        DetectorContext ctx = DetectorTestUtils.contextFor("src/empty.ts", "typescript", "");
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
    }

    @Test
    void deterministic() {
        String code = "import { Controller, Get } from '@nestjs/common';\n@Controller('test')\nexport class TestController {\n    @Get()\n    find() {}\n}";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/test.controller.ts", "typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript.nestjs_controllers", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript");
    }
}
