package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BackendGenerationFixerTest {

    @TempDir
    Path tempDir;

    @Test
    void fixesCharacterImportAndSelectCount() throws Exception {
        Path file = tempDir.resolve("QuizService.java");
        Files.writeString(file, """
                package demo;
                import com.demo.entity.*;
                public class QuizService {
                  Character character;
                  int count = (int) characterMapper.selectCount(null);
                }
                """);

        BackendGenerationFixer fixer = new BackendGenerationFixer();
        fixer.fixJavaFile(file);

        String updated = Files.readString(file);
        assertTrue(updated.contains("import com.demo.entity.Character;"));
        assertTrue(updated.contains("Math.toIntExact(characterMapper.selectCount(null))"));
    }

    @Test
    void upgradesMybatisPlusStarterForSpringBoot3Pom() throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        Files.writeString(pomFile, """
                <project>
                  <parent>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-starter-parent</artifactId>
                    <version>3.2.0</version>
                  </parent>
                  <dependencies>
                    <dependency>
                      <groupId>com.baomidou</groupId>
                      <artifactId>mybatis-plus-boot-starter</artifactId>
                      <version>3.5.5</version>
                    </dependency>
                  </dependencies>
                </project>
                """);

        BackendGenerationFixer fixer = new BackendGenerationFixer();
        fixer.fixPomFile(pomFile);

        String updated = Files.readString(pomFile);
        assertTrue(updated.contains("mybatis-plus-spring-boot3-starter"));
        assertFalse(updated.contains("mybatis-plus-boot-starter"));
    }
}
