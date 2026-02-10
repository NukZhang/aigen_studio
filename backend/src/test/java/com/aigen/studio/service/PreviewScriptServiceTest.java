package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class PreviewScriptServiceTest {

    @Test
    void createsStartScriptWhenMissing(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));

        PreviewScriptService service = new PreviewScriptService();
        service.ensureFrontendStartScript(frontendDir);

        Path scriptPath = frontendDir.resolve("scripts/start-preview.sh");
        assertTrue(Files.exists(scriptPath));
        String content = Files.readString(scriptPath);
        assertTrue(content.contains("PORT=${1:-3002}"));
        assertTrue(content.contains("exec npm run dev"));
        assertTrue(content.contains("BASE_PATH"));
    }

    @Test
    void updatesExistingScriptToUseExec(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Path scriptPath = frontendDir.resolve("scripts/start-preview.sh");
        Files.createDirectories(scriptPath.getParent());
        Files.writeString(scriptPath, String.join("\n",
                "#!/usr/bin/env sh",
                "set -e",
                "PORT=${1:-3002}",
                "BASE_PATH=${2:-/__preview__/}",
                "npm run dev -- --port \"$PORT\" --strictPort --base \"$BASE_PATH\"",
                ""
        ));

        PreviewScriptService service = new PreviewScriptService();
        service.ensureFrontendStartScript(frontendDir);

        String updated = Files.readString(scriptPath);
        assertTrue(updated.contains("exec npm run dev"));
    }

    @Test
    void updatesRouterBaseToViteBase(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Path routerDir = Files.createDirectories(frontendDir.resolve("src/router"));
        Path routerFile = routerDir.resolve("index.js");
        Files.writeString(routerFile, String.join("\n",
                "import { createRouter, createWebHistory } from 'vue-router'",
                "const router = createRouter({",
                "  history: createWebHistory(),",
                "  routes: []",
                "})",
                ""
        ));

        PreviewScriptService service = new PreviewScriptService();
        invokeEnsureRouterBase(service, frontendDir);

        String updated = Files.readString(routerFile);
        assertTrue(updated.contains("createWebHistory(import.meta.env.BASE_URL)"));
    }

    private void invokeEnsureRouterBase(PreviewScriptService service, Path frontendDir) {
        try {
            Method method = PreviewScriptService.class.getMethod("ensureFrontendRouterBase", Path.class);
            method.invoke(service, frontendDir);
        } catch (NoSuchMethodException e) {
            fail("Missing method ensureFrontendRouterBase in PreviewScriptService");
        } catch (Exception e) {
            fail("Failed to invoke ensureFrontendRouterBase: " + e.getMessage());
        }
    }

    @Test
    void updatesViteProxyTargetToPreviewBackendPort(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Path viteConfig = frontendDir.resolve("vite.config.ts");
        Files.writeString(viteConfig, """
                import { defineConfig } from 'vite'
                export default defineConfig({
                  server: {
                    proxy: {
                      '/api': {
                        target: 'http://localhost:8080',
                        changeOrigin: true
                      }
                    }
                  }
                })
                """);

        PreviewScriptService service = new PreviewScriptService();
        service.ensureFrontendApiProxyTarget(frontendDir, 8081);

        String updated = Files.readString(viteConfig);
        assertTrue(updated.contains("'/subapi': {"));
        assertTrue(updated.contains("target: 'http://localhost:8081'"));
        assertTrue(updated.contains("rewrite: (path) => path.replace(/^\\/subapi/, '/api')"));
    }

    @Test
    void rewritesFrontendApiBasePathToSubapi(@TempDir Path tmp) throws Exception {
        Path frontendDir = Files.createDirectories(tmp.resolve("frontend"));
        Path srcApiDir = Files.createDirectories(frontendDir.resolve("src/api"));
        Path apiFile = srcApiDir.resolve("http.ts");
        Files.writeString(apiFile, """
                import axios from 'axios'
                export const http = axios.create({
                  baseURL: '/api',
                  timeout: 10000
                })
                """);

        PreviewScriptService service = new PreviewScriptService();
        service.ensureFrontendApiBasePath(frontendDir, "/subapi");

        String updated = Files.readString(apiFile);
        assertTrue(updated.contains("baseURL: '/subapi'"));
        assertFalse(updated.contains("baseURL: '/api'"));
    }
}
