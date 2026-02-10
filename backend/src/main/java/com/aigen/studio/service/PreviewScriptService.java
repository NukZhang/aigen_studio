package com.aigen.studio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
    private static final List<String> VITE_CONFIG_CANDIDATES = List.of(
            "vite.config.ts",
            "vite.config.js",
            "vite.config.mjs",
            "vite.config.cjs"
    );
    private static final Pattern WEB_HISTORY_EMPTY_ARGS = Pattern.compile("createWebHistory\\s*\\(\\s*\\)");
    private static final Pattern VITE_PROXY_TARGET = Pattern.compile("target\\s*:\\s*['\"]http://localhost:\\d+['\"]");
    private static final Pattern SUBAPI_PROXY_BLOCK = Pattern.compile("([\"'])/subapi\\1\\s*:\\s*\\{");
    private static final Pattern FRONTEND_API_BASE_PATH = Pattern.compile("([\"'])/api(?=[/'\"?,\\s]|$)");

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

    public void ensureFrontendApiProxyTarget(Path frontendDir, int backendPort) {
        if (frontendDir == null || !Files.isDirectory(frontendDir) || backendPort <= 0) {
            return;
        }
        Path viteConfig = resolveViteConfigFile(frontendDir);
        if (viteConfig == null) {
            return;
        }

        try {
            String content = Files.readString(viteConfig);
            if (!containsApiProxy(content)) {
                return;
            }
            String expectedTarget = "target: 'http://localhost:" + backendPort + "'";
            String rewriteExpression = "path.replace(/^\\/subapi/, '/api')";
            String updated = content
                    .replace("'/api':", "'/subapi':")
                    .replace("\"/api\":", "\"/subapi\":");
            updated = VITE_PROXY_TARGET.matcher(updated).replaceAll(expectedTarget);
            if (!updated.contains(rewriteExpression)) {
                Matcher matcher = SUBAPI_PROXY_BLOCK.matcher(updated);
                if (matcher.find()) {
                    String blockStart = matcher.group();
                    String replacement = Matcher.quoteReplacement(
                            blockStart + "\n        rewrite: (path) => " + rewriteExpression + ","
                    );
                    updated = matcher.replaceFirst(replacement);
                }
            }
            if (!content.equals(updated)) {
                Files.writeString(viteConfig, updated);
                log.info("Updated frontend API proxy target to port {} at {}", backendPort, viteConfig);
            }
        } catch (IOException e) {
            log.warn("Failed to update frontend API proxy at {}", viteConfig, e);
        }
    }

    public void ensureFrontendApiBasePath(Path frontendDir, String apiBasePath) {
        if (frontendDir == null || !Files.isDirectory(frontendDir) || apiBasePath == null || apiBasePath.isBlank()) {
            return;
        }
        String normalizedBasePath = apiBasePath.startsWith("/") ? apiBasePath : "/" + apiBasePath;
        Path srcDir = frontendDir.resolve("src");
        if (!Files.isDirectory(srcDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(srcDir)) {
            AtomicInteger scannedFiles = new AtomicInteger();
            AtomicInteger updatedFiles = new AtomicInteger();
            paths.filter(Files::isRegularFile)
                    .filter(path -> isFrontendSourceFile(path.getFileName().toString()))
                    .forEach(path -> {
                        scannedFiles.incrementAndGet();
                        if (rewriteFrontendApiBasePath(path, normalizedBasePath)) {
                            updatedFiles.incrementAndGet();
                        }
                    });
            log.info(
                    "Frontend API base path rewrite completed under {}: target={}, scannedFiles={}, updatedFiles={}",
                    srcDir,
                    normalizedBasePath,
                    scannedFiles.get(),
                    updatedFiles.get()
            );
        } catch (IOException e) {
            log.warn("Failed to rewrite frontend api base path under {}", srcDir, e);
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

    private Path resolveViteConfigFile(Path frontendDir) {
        for (String candidate : VITE_CONFIG_CANDIDATES) {
            Path path = frontendDir.resolve(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private boolean containsApiProxy(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("proxy")
                && (content.contains("'/api'")
                || content.contains("\"/api\"")
                || content.contains("'/subapi'")
                || content.contains("\"/subapi\""));
    }

    private String updateRouterBase(String content) {
        if (content.contains("createWebHistory(import.meta.env.BASE_URL)")) {
            return content;
        }
        return WEB_HISTORY_EMPTY_ARGS.matcher(content)
                .replaceAll("createWebHistory(import.meta.env.BASE_URL)");
    }

    private boolean rewriteFrontendApiBasePath(Path sourceFile, String apiBasePath) {
        try {
            String content = Files.readString(sourceFile);
            String replacement = "$1" + apiBasePath;
            String updated = FRONTEND_API_BASE_PATH.matcher(content).replaceAll(replacement);
            if (!content.equals(updated)) {
                Files.writeString(sourceFile, updated);
                return true;
            }
        } catch (IOException e) {
            log.warn("Failed to rewrite api base path in {}", sourceFile, e);
        }
        return false;
    }

    private boolean isFrontendSourceFile(String fileName) {
        return fileName.endsWith(".ts")
                || fileName.endsWith(".js")
                || fileName.endsWith(".tsx")
                || fileName.endsWith(".jsx")
                || fileName.endsWith(".vue");
    }
}
