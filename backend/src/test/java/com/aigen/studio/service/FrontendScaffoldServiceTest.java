package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrontendScaffoldServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void stripsSideEffectTagsFromTemplate() throws Exception {
        FrontendScaffoldService service = new FrontendScaffoldService();
        Path frontendDir = tempDir.resolve("frontend");
        String html = "<html><head><style>.x{color:red;}</style></head>" +
                "<body><div class=\"x\">Hello</div><script>console.log('x')</script></body></html>";

        service.ensureVueScaffoldAndInjectPrototype(frontendDir, html, "Demo");

        Path appVue = frontendDir.resolve("src").resolve("App.vue");
        assertTrue(Files.exists(appVue));
        String content = Files.readString(appVue);

        String templateSection = extractSection(content, "<template>", "</template>");
        assertFalse(templateSection.contains("<script"), "template should not contain script tags");
        assertFalse(templateSection.contains("<style"), "template should not contain style tags");
    }

    @Test
    void keepsExistingAppVueWhenAiAlreadyGeneratedEntry() throws Exception {
        FrontendScaffoldService service = new FrontendScaffoldService();
        Path frontendDir = tempDir.resolve("frontend");
        Path srcDir = frontendDir.resolve("src");
        Files.createDirectories(srcDir);
        Path appVue = srcDir.resolve("App.vue");
        String existingApp = """
                <template>
                  <router-view />
                </template>
                """;
        Files.writeString(appVue, existingApp);

        String prototypeHtml = "<html><body><div class=\"prototype\">prototype</div></body></html>";
        service.ensureVueScaffoldAndInjectPrototype(frontendDir, prototypeHtml, "Demo");

        String updated = Files.readString(appVue);
        assertTrue(updated.contains("<router-view"));
        assertFalse(updated.contains("prototype"));
    }

    private String extractSection(String content, String start, String end) {
        int startIndex = content.indexOf(start);
        int endIndex = content.indexOf(end);
        if (startIndex < 0 || endIndex < 0 || endIndex <= startIndex) {
            return "";
        }
        return content.substring(startIndex + start.length(), endIndex);
    }
}
