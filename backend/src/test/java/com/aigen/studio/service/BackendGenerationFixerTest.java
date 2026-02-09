package com.aigen.studio.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
