package io.github.randomcodespace.iq.analyzer;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ArchitectureKeywordFilterTest {

    private final ArchitectureKeywordFilter filter = new ArchitectureKeywordFilter();

    // ---- Java ----

    @Test
    void javaRestControllerShouldAnalyze() {
        String content = """
                package com.example;

                @RestController
                @RequestMapping("/users")
                public class UserController {
                    @GetMapping
                    public List<User> list() { return List.of(); }
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "java"));
    }

    @Test
    void javaUtilityClassShouldSkip() {
        String content = """
                package com.example.util;

                public class StringUtils {
                    public static String capitalize(String s) {
                        if (s == null || s.isEmpty()) return s;
                        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
                    }

                    public static boolean isBlank(String s) {
                        return s == null || s.trim().isEmpty();
                    }
                }
                """;
        assertFalse(filter.shouldAnalyze(content, "java"));
    }

    @Test
    void javaServiceAnnotationShouldAnalyze() {
        String content = """
                package com.example;

                import java.util.List;

                @Service
                public class UserService {
                    public List<String> getUsers() { return List.of(); }
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "java"));
    }

    @Test
    void javaRepositoryShouldAnalyze() {
        String content = """
                package com.example;

                public interface UserRepository extends JpaRepository<User, Long> {}
                """;
        assertTrue(filter.shouldAnalyze(content, "java"));
    }

    @Test
    void javaEntityShouldAnalyze() {
        String content = """
                package com.example;

                @Entity
                @Table(name = "users")
                public class User {
                    private Long id;
                    private String name;
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "java"));
    }

    // ---- Python ----

    @Test
    void pythonFastApiShouldAnalyze() {
        String content = """
                from fastapi import FastAPI, Depends

                app = FastAPI()

                @app.get("/users")
                async def list_users():
                    return []
                """;
        assertTrue(filter.shouldAnalyze(content, "python"));
    }

    @Test
    void pythonDjangoModelShouldAnalyze() {
        String content = """
                from django.db import models

                class User(models.Model):
                    name = models.CharField(max_length=100)
                    email = models.EmailField()
                """;
        assertTrue(filter.shouldAnalyze(content, "python"));
    }

    // ---- TypeScript ----

    @Test
    void typescriptExpressRouterShouldAnalyze() {
        String content = """
                import express, { Router } from 'express';

                const router = Router();

                router.get('/users', async (req, res) => {
                    res.json([]);
                });

                export default router;
                """;
        assertTrue(filter.shouldAnalyze(content, "typescript"));
    }

    @Test
    void typescriptNestControllerShouldAnalyze() {
        String content = """
                import { Controller, Get } from '@nestjs/common';

                @Controller('users')
                export class UserController {
                    @Get()
                    findAll() { return []; }
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "typescript"));
    }

    // ---- Go ----

    @Test
    void goHttpHandlerShouldAnalyze() {
        String content = """
                package main

                import "net/http"

                func main() {
                    http.HandleFunc("/users", listUsers)
                    http.ListenAndServe(":8080", nil)
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "go"));
    }

    // ---- C# ----

    @Test
    void csharpApiControllerShouldAnalyze() {
        String content = """
                [ApiController]
                [Route("api/[controller]")]
                public class UsersController : ControllerBase {
                    [HttpGet]
                    public IActionResult Get() => Ok(new List<User>());
                }
                """;
        assertTrue(filter.shouldAnalyze(content, "csharp"));
    }

    // ---- Plain text ----

    @Test
    void plainTextFileShouldSkip() {
        String content = """
                This is a plain text file.
                It has no code or keywords.
                Just some words.
                """;
        assertFalse(filter.shouldAnalyze(content, "text"));
    }

    @Test
    void emptyStringShouldSkip() {
        assertFalse(filter.shouldAnalyze("", "java"));
    }

    @Test
    void blankStringShouldSkip() {
        assertFalse(filter.shouldAnalyze("   \n\t  ", "java"));
    }

    @Test
    void nullContentShouldSkip() {
        assertFalse(filter.shouldAnalyze((String) null, "java"));
    }

    // ---- Raw bytes overload ----

    @Test
    void rawBytesRestControllerShouldAnalyze() {
        byte[] content = "@RestController\npublic class Ctrl {}".getBytes(StandardCharsets.UTF_8);
        assertTrue(filter.shouldAnalyze(content, "java"));
    }

    @Test
    void rawBytesEmptyShouldSkip() {
        assertFalse(filter.shouldAnalyze(new byte[0], "java"));
    }

    @Test
    void rawBytesNullShouldSkip() {
        assertFalse(filter.shouldAnalyze((byte[]) null, "java"));
    }

    // ---- Generic keywords ----

    @Test
    void genericRouterKeywordShouldAnalyze() {
        String content = "const router = new Router();";
        assertTrue(filter.shouldAnalyze(content, "unknown-lang"));
    }

    @Test
    void genericHandlerKeywordShouldAnalyze() {
        String content = "function handler(req, res) {}";
        assertTrue(filter.shouldAnalyze(content, "unknown-lang"));
    }

    // ---- Language case insensitivity ----

    @Test
    void languageNameCaseInsensitive() {
        String content = "@RestController\npublic class Ctrl {}";
        assertTrue(filter.shouldAnalyze(content, "Java"));
        assertTrue(filter.shouldAnalyze(content, "JAVA"));
    }

    // ---- Determinism ----

    @Test
    void deterministicResults() {
        String content = "@RestController\npublic class UserController {}";
        boolean first = filter.shouldAnalyze(content, "java");
        boolean second = filter.shouldAnalyze(content, "java");
        assertEquals(first, second);
    }

    @Test
    void deterministicResultsForSkippedFile() {
        String content = "public class StringUtils { public static String trim(String s) { return s; } }";
        boolean first = filter.shouldAnalyze(content, "java");
        boolean second = filter.shouldAnalyze(content, "java");
        assertEquals(first, second);
    }

    // ---- JavaScript uses same keywords as TypeScript ----

    @Test
    void javascriptExpressShouldAnalyze() {
        String content = "const express = require('express');\nconst app = express();";
        assertTrue(filter.shouldAnalyze(content, "javascript"));
    }
}
