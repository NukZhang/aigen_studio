package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
