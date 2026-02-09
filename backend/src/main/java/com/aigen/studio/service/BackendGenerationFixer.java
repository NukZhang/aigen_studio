package com.aigen.studio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class BackendGenerationFixer {

    private static final Pattern ENTITY_WILDCARD = Pattern.compile("import\\s+([\\w.]+\\.entity)\\.\\*;");
    private static final Pattern CHARACTER_USAGE = Pattern.compile("\\bCharacter\\b");
    private static final Pattern SELECT_COUNT_CAST = Pattern.compile(
            "int\\s+(\\w+)\\s*=\\s*\\(int\\)\\s*([\\w.]+)\\.selectCount\\(null\\)\\s*;?"
    );

    public void fixGeneratedBackend(Path backendDir) {
        if (backendDir == null || !Files.isDirectory(backendDir)) {
            return;
        }
        try (var paths = Files.walk(backendDir)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::fixJavaFile);
        } catch (Exception e) {
            log.warn("Backend fixer failed", e);
        }
    }

    void fixJavaFile(Path file) {
        try {
            String content = Files.readString(file);
            String updated = content;

            Matcher wildcard = ENTITY_WILDCARD.matcher(updated);
            if (wildcard.find()
                    && CHARACTER_USAGE.matcher(updated).find()
                    && !updated.contains("import " + wildcard.group(1) + ".Character")) {
                String importLine = "import " + wildcard.group(1) + ".Character;\n";
                updated = updated.replace(wildcard.group(0), importLine + wildcard.group(0));
            }

            Matcher selectCount = SELECT_COUNT_CAST.matcher(updated);
            if (selectCount.find()) {
                updated = selectCount.replaceAll("int $1 = Math.toIntExact($2.selectCount(null));");
            }

            if (!updated.equals(content)) {
                Files.writeString(file, updated);
            }
        } catch (Exception e) {
            log.warn("Failed to fix file {}", file, e);
        }
    }
}
