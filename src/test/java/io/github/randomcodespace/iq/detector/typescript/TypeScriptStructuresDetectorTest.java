package io.github.randomcodespace.iq.detector.typescript;

import io.github.randomcodespace.iq.detector.DetectorContext;
import io.github.randomcodespace.iq.detector.DetectorResult;
import io.github.randomcodespace.iq.detector.DetectorTestUtils;
import io.github.randomcodespace.iq.model.EdgeKind;
import io.github.randomcodespace.iq.model.NodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
    void detectsInterface() {
        String code = """
                export interface UserDTO {
                    id: string;
                    name: string;
                }
                interface InternalState {
                    count: number;
                }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long ifaces = result.nodes().stream().filter(n -> n.getKind() == NodeKind.INTERFACE).count();
        assertEquals(2, ifaces);
        assertThat(result.nodes()).anyMatch(n -> "UserDTO".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "InternalState".equals(n.getLabel()));
    }

    @Test
    void detectsTypeAlias() {
        String code = """
                export type UserId = string;
                type Handler = (req: Request) => Response;
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/types.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.CLASS
                && "UserId".equals(n.getLabel())
                && Boolean.TRUE.equals(n.getProperties().get("type_alias")));
        assertThat(result.nodes()).anyMatch(n -> "Handler".equals(n.getLabel()));
    }

    @Test
    void detectsClass() {
        String code = """
                export class UserService {
                    findAll() {}
                }
                export abstract class BaseService {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.service.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long classes = result.nodes().stream().filter(n -> n.getKind() == NodeKind.CLASS).count();
        assertEquals(2, classes);
        assertThat(result.nodes()).anyMatch(n -> "UserService".equals(n.getLabel()));
    }

    @Test
    void detectsNamedFunction() {
        String code = """
                export function getUser(id: string) {}
                export async function createUser(data: any) {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/user.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.METHOD && "getUser".equals(n.getLabel()));
        var createUserFn = result.nodes().stream()
                .filter(n -> "createUser".equals(n.getLabel()))
                .findFirst();
        assertTrue(createUserFn.isPresent());
        assertEquals(NodeKind.METHOD, createUserFn.get().getKind());
    }

    @Test
    void detectsArrowFunction() {
        String code = """
                export const handleRequest = async (req: Request) => {
                    return req.body;
                };
                const helper = (x: number) => x * 2;
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/handler.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.METHOD && "handleRequest".equals(n.getLabel()));
    }

    @Test
    void detectsEnum() {
        String code = """
                export enum UserRole { ADMIN, USER, GUEST }
                export const enum Direction { Up, Down, Left, Right }
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/enums.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        long enums = result.nodes().stream().filter(n -> n.getKind() == NodeKind.ENUM).count();
        assertEquals(2, enums);
        assertThat(result.nodes()).anyMatch(n -> "UserRole".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "Direction".equals(n.getLabel()));
    }

    @Test
    void detectsNamespace() {
        String code = """
                export namespace Utils {
                    export function helper() {}
                }
                namespace Internal {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/utils.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertThat(result.nodes()).anyMatch(n -> n.getKind() == NodeKind.MODULE && "Utils".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "Internal".equals(n.getLabel()));
    }

    @Test
    void detectsImportsAsEdges() {
        String code = """
                import { UserService } from './user.service';
                import { DatabaseModule } from '@app/database';
                import express from 'express';
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/app.ts", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertEquals(3, result.edges().size());
        assertThat(result.edges()).allMatch(e -> e.getKind() == EdgeKind.IMPORTS);
    }

    @Test
    void detectsDefaultExportFunction() {
        String code = "export default function HomePage() { return null; }";
        DetectorContext ctx = DetectorTestUtils.contextFor("src/home.tsx", "typescript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertThat(result.nodes()).anyMatch(n -> "HomePage".equals(n.getLabel()) && n.getKind() == NodeKind.METHOD);
    }

    @Test
    void noMatchOnEmptyFile() {
        String code = "";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorResult result = detector.detect(ctx);
        assertTrue(result.nodes().isEmpty());
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

    @Test
    void worksForJavaScriptFiles() {
        String code = """
                const express = require('express');
                function createRouter() {}
                class UserController {}
                """;
        DetectorContext ctx = DetectorTestUtils.contextFor("src/router.js", "javascript", code);
        DetectorResult result = detector.detect(ctx);

        assertFalse(result.nodes().isEmpty());
        assertThat(result.nodes()).anyMatch(n -> "createRouter".equals(n.getLabel()));
        assertThat(result.nodes()).anyMatch(n -> "UserController".equals(n.getLabel()));
    }

    @Test
    void deterministic() {
        String code = "interface A {}\nclass B {}\nfunction c() {}\nexport enum D { X }";
        DetectorContext ctx = DetectorTestUtils.contextFor("typescript", code);
        DetectorTestUtils.assertDeterministic(detector, ctx);
    }

    @Test
    void getName() {
        assertEquals("typescript_structures", detector.getName());
    }

    @Test
    void getSupportedLanguages() {
        assertThat(detector.getSupportedLanguages()).contains("typescript", "javascript");
    }
}
