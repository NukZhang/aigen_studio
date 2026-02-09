package com.aigen.studio.service;

import com.aigen.studio.sdk.iflow.IFlowClientHelper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTaskServiceTest {

    @Test
    void buildCodeGenerationPromptRequiresFrontendEntrySkeleton() throws Exception {
        PromptTaskService service = new PromptTaskService(new IFlowClientHelper());
        Method method = PromptTaskService.class.getDeclaredMethod(
                "buildCodeGenerationPrompt",
                String.class,
                Path.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(
                service,
                "{\"modules\":[{\"name\":\"frontend\",\"type\":\"vue3\"}]}",
                Path.of("/tmp/output")
        );

        assertTrue(prompt.contains("frontend/index.html"));
        assertTrue(prompt.contains("src/main.ts"));
        assertTrue(prompt.contains("src/App.vue"));
        assertTrue(prompt.contains("vite.config"));
        assertTrue(prompt.contains("package.json"));
        assertTrue(prompt.contains("createWebHistory(import.meta.env.BASE_URL)"));
        assertTrue(prompt.contains("不要只创建目录"));
    }

    @Test
    void buildCodeGenerationPromptUsesJakartaAndMapperRequirements() throws Exception {
        PromptTaskService service = new PromptTaskService(new IFlowClientHelper());
        Method method = PromptTaskService.class.getDeclaredMethod(
                "buildCodeGenerationPrompt",
                String.class,
                Path.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(
                service,
                "{\"modules\":[{\"name\":\"backend\",\"type\":\"springboot\"}]}",
                Path.of("/tmp/output")
        );

        assertTrue(prompt.contains("jakarta.servlet"));
        assertTrue(prompt.contains("javax.servlet"));
        assertTrue(prompt.contains("MyBatis-Plus"));
        assertTrue(prompt.contains("Mapper"));
        assertTrue(prompt.contains("Character"));
        assertTrue(prompt.contains("通配符"));
        assertTrue(prompt.contains("Math.toIntExact"));
    }

    @Test
    void buildCodeGenerationPromptRequiresH2CompatibleSchema() throws Exception {
        PromptTaskService service = new PromptTaskService(new IFlowClientHelper());
        Method method = PromptTaskService.class.getDeclaredMethod(
                "buildCodeGenerationPrompt",
                String.class,
                Path.class
        );
        method.setAccessible(true);

        String prompt = (String) method.invoke(
                service,
                "{\"modules\":[{\"name\":\"backend\",\"type\":\"springboot\"}]}",
                Path.of("/tmp/output")
        );

        assertTrue(prompt.contains("schema.sql"));
        assertTrue(prompt.contains("H2"));
        assertTrue(prompt.contains("CREATE DATABASE"));
        assertTrue(prompt.contains("ENGINE="));
        assertTrue(prompt.contains("CHARSET"));
        assertTrue(prompt.contains("COLLATE"));
        assertTrue(prompt.contains("CREATE INDEX"));
        assertTrue(prompt.contains("CREATE TABLE IF NOT EXISTS"));
    }
}
