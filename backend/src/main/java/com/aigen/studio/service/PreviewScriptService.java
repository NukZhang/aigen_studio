package com.aigen.studio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PreviewScriptService {

    private static final String SCRIPT_RELATIVE_PATH = "scripts/start-preview.sh";
    private static final List<String> ROUTER_CANDIDATES = List.of(
            "src/router/index.js",
            "src/router/index.ts",
            "src/router/index.jsx",
            "src/router/index.tsx"
    );
    private static final Pattern WEB_HISTORY_EMPTY_ARGS = Pattern.compile("createWebHistory\\s*\\(\\s*\\)");

    public Path ensureFrontendStartScript(Path frontendDir) {
        if (frontendDir == null || !Files.isDirectory(frontendDir)) {
            return null;
        }

        Path scriptPath = frontendDir.resolve(SCRIPT_RELATIVE_PATH);
        String desiredContent = buildScriptContent();
        if (Files.exists(scriptPath)) {
            try {
                String existing = Files.readString(scriptPath);
                if (!existing.contains("exec npm run dev")) {
                    Files.writeString(scriptPath, desiredContent);
                }
            } catch (IOException e) {
                log.warn("Failed to update preview start script at {}", scriptPath, e);
            }
            return scriptPath;
        }

        try {
            Files.createDirectories(scriptPath.getParent());
            Files.writeString(scriptPath, desiredContent);
            return scriptPath;
        } catch (IOException e) {
            log.warn("Failed to create preview start script at {}", scriptPath, e);
            return null;
        }
    }

    public void ensureFrontendRouterBase(Path frontendDir) {
        if (frontendDir == null || !Files.isDirectory(frontendDir)) {
            return;
        }

        Path routerFile = resolveRouterFile(frontendDir);
        if (routerFile == null) {
            return;
        }

        try {
            String content = Files.readString(routerFile);
            String updated = updateRouterBase(content);
            if (!content.equals(updated)) {
                Files.writeString(routerFile, updated);
            }
        } catch (IOException e) {
            log.warn("Failed to update router base at {}", routerFile, e);
        }
    }

    private String buildScriptContent() {
        return String.join("\n",
                "#!/usr/bin/env sh",
                "set -e",
                "PORT=${1:-3002}",
                "BASE_PATH=${2:-/__preview__/}",
                "exec npm run dev -- --port \"$PORT\" --strictPort --base \"$BASE_PATH\"",
                ""
        );
    }

    private Path resolveRouterFile(Path frontendDir) {
        for (String candidate : ROUTER_CANDIDATES) {
            Path path = frontendDir.resolve(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private String updateRouterBase(String content) {
        if (content.contains("createWebHistory(import.meta.env.BASE_URL)")) {
            return content;
        }
        return WEB_HISTORY_EMPTY_ARGS.matcher(content)
                .replaceAll("createWebHistory(import.meta.env.BASE_URL)");
    }
}
