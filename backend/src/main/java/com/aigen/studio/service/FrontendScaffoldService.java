package com.aigen.studio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class FrontendScaffoldService {

    public void ensureVueScaffoldAndInjectPrototype(Path frontendDir, String uiPrototypeHtml, String projectName) {
        if (frontendDir == null) {
            throw new IllegalArgumentException("frontendDir must not be null");
        }

        try {
            Path srcDir = frontendDir.resolve("src");
            Files.createDirectories(srcDir);

            ensureIndexHtml(frontendDir, projectName);
            ensureMainTs(srcDir);
            ensureStyleCss(srcDir);
            ensureViteConfig(frontendDir);
            ensurePackageJson(frontendDir, projectName);

            if (uiPrototypeHtml != null && !uiPrototypeHtml.isBlank()) {
                writeAppVueFromPrototype(srcDir, uiPrototypeHtml);
            } else {
                ensureDefaultAppVue(srcDir);
            }
        } catch (Exception e) {
            log.error("Failed to ensure Vue scaffold", e);
            throw new RuntimeException("Failed to ensure Vue scaffold", e);
        }
    }

    private void ensureIndexHtml(Path frontendDir, String projectName) throws Exception {
        Path indexHtml = frontendDir.resolve("index.html");
        String title = (projectName == null || projectName.isBlank()) ? "AIGen App" : projectName;
        String content = """
                <!DOCTYPE html>
                <html lang=\"zh-CN\">
                  <head>
                    <meta charset=\"UTF-8\" />
                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
                    <title>%s</title>
                  </head>
                  <body>
                    <div id=\"app\"></div>
                    <script type=\"module\" src=\"/src/main.ts\"></script>
                  </body>
                </html>
                """.formatted(title);

        if (!Files.exists(indexHtml)) {
            Files.writeString(indexHtml, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        String existing = Files.readString(indexHtml);
        if (!existing.contains("id=\"app\"") || !existing.contains("/src/main.ts")) {
            Files.writeString(indexHtml, content, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void ensureMainTs(Path srcDir) throws Exception {
        Path mainTs = srcDir.resolve("main.ts");
        if (Files.exists(mainTs)) {
            return;
        }
        String content = """
                import { createApp } from 'vue'
                import App from './App.vue'
                import './style.css'

                createApp(App).mount('#app')
                """;
        Files.writeString(mainTs, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void ensureStyleCss(Path srcDir) throws Exception {
        Path styleCss = srcDir.resolve("style.css");
        if (!Files.exists(styleCss)) {
            Files.writeString(styleCss, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private void ensureViteConfig(Path frontendDir) throws Exception {
        Path viteConfig = frontendDir.resolve("vite.config.ts");
        if (Files.exists(viteConfig)) {
            return;
        }
        String content = """
                import { defineConfig } from 'vite'
                import vue from '@vitejs/plugin-vue'

                export default defineConfig({
                  plugins: [vue()],
                })
                """;
        Files.writeString(viteConfig, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void ensurePackageJson(Path frontendDir, String projectName) throws Exception {
        Path packageJson = frontendDir.resolve("package.json");
        if (Files.exists(packageJson)) {
            return;
        }
        String name = (projectName == null || projectName.isBlank())
                ? "aigen-frontend"
                : projectName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        String content = """
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "vue": "^3.4.21"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-vue": "^5.0.4",
                    "vite": "^5.1.6",
                    "typescript": "^5.4.2"
                  }
                }
                """.formatted(name);
        Files.writeString(packageJson, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void writeAppVueFromPrototype(Path srcDir, String uiPrototypeHtml) throws Exception {
        String body = extractBody(uiPrototypeHtml);
        String style = extractStyle(uiPrototypeHtml);

        if (body.isBlank()) {
            body = "<div class=\"ui-prototype\"></div>";
        } else {
            body = "<div class=\"ui-prototype\">\n" + indent(body, 4) + "\n</div>";
        }

        StringBuilder content = new StringBuilder();
        content.append("<template>\n  ")
                .append(body.replace("\n", "\n  "))
                .append("\n</template>\n\n");
        content.append("<script setup lang=\"ts\">\n</script>\n");
        if (!style.isBlank()) {
            content.append("\n<style>\n").append(style).append("\n</style>\n");
        }

        Path appVue = srcDir.resolve("App.vue");
        Files.writeString(appVue, content.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private void ensureDefaultAppVue(Path srcDir) throws Exception {
        Path appVue = srcDir.resolve("App.vue");
        if (Files.exists(appVue)) {
            return;
        }
        String content = """
                <template>
                  <div class=\"app\"></div>
                </template>

                <script setup lang=\"ts\">
                </script>
                """;
        Files.writeString(appVue, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String extractBody(String html) {
        if (html == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("(?is)<body[^>]*>(.*?)</body>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String cleaned = html
                .replaceAll("(?is)<!doctype[^>]*>", "")
                .replaceAll("(?is)<head[^>]*>.*?</head>", "")
                .replaceAll("(?is)<html[^>]*>", "")
                .replaceAll("(?is)</html>", "")
                .replaceAll("(?is)<body[^>]*>", "")
                .replaceAll("(?is)</body>", "")
                .trim();
        return cleaned;
    }

    private String extractStyle(String html) {
        if (html == null) {
            return "";
        }
        Pattern pattern = Pattern.compile("(?is)<style[^>]*>(.*?)</style>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return text.replace("\n", "\n" + prefix);
    }
}
