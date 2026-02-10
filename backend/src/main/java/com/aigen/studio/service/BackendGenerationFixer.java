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
    private static final Pattern SPRING_BOOT_PARENT_VERSION = Pattern.compile(
            "<parent>[\\s\\S]*?<artifactId>spring-boot-starter-parent</artifactId>[\\s\\S]*?<version>\\s*3\\.[^<]*</version>[\\s\\S]*?</parent>"
    );
    private static final String MYBATIS_PLUS_BOOT2_ARTIFACT = "<artifactId>mybatis-plus-boot-starter</artifactId>";
    private static final String MYBATIS_PLUS_BOOT3_ARTIFACT = "<artifactId>mybatis-plus-spring-boot3-starter</artifactId>";

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
        fixPomFile(backendDir.resolve("pom.xml"));
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

    void fixPomFile(Path pomFile) {
        if (pomFile == null || !Files.exists(pomFile)) {
            return;
        }
        try {
            String content = Files.readString(pomFile);
            String updated = content;

            boolean isSpringBoot3Project = SPRING_BOOT_PARENT_VERSION.matcher(content).find();
            if (isSpringBoot3Project
                    && content.contains(MYBATIS_PLUS_BOOT2_ARTIFACT)
                    && !content.contains(MYBATIS_PLUS_BOOT3_ARTIFACT)) {
                updated = updated.replace(MYBATIS_PLUS_BOOT2_ARTIFACT, MYBATIS_PLUS_BOOT3_ARTIFACT);
                log.info("Updated MyBatis-Plus starter to Spring Boot 3 compatible artifact: {}", pomFile);
            }

            if (!updated.equals(content)) {
                Files.writeString(pomFile, updated);
            }
        } catch (Exception e) {
            log.warn("Failed to fix pom file {}", pomFile, e);
        }
    }
}
