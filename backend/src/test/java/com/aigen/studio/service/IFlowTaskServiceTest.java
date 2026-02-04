package com.aigen.studio.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IFlowTaskServiceTest {

    @Test
    void buildCodeGenerationPromptRequiresFrontendEntrySkeleton() throws Exception {
        IFlowTaskService service = new IFlowTaskService(new IFlowClientHelper());
        Method method = IFlowTaskService.class.getDeclaredMethod(
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
}
