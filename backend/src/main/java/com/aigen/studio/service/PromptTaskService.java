package com.aigen.studio.service;

import com.aigen.studio.sdk.ICodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Prompt 任务服务
 * 提供具体的任务执行逻辑，如需求理解、代码生成等
 * 通过 ICodingService 接口与 SDK 交互，不依赖具体的 SDK 实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTaskService {

    private final ICodingService codingService;
    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Value("${aigen.prompt.understanding-template:classpath:prompt-templates/understanding-prompt.md}")
    private String understandingPromptTemplateLocation = "classpath:prompt-templates/understanding-prompt.md";

    @Value("${aigen.prompt.code-generation-template:classpath:prompt-templates/code-generation-prompt.md}")
    private String codeGenerationPromptTemplateLocation = "classpath:prompt-templates/code-generation-prompt.md";

    @Value("${iflow.sdk.code-task-timeout-ms:600000}")
    private long codeTaskTimeoutMillis = 600000L;

    /**
     * 理解需求
     */
    public String understandRequirement(String userRequirement) {
        log.info("Understanding requirement: {}", userRequirement);

        StringBuilder result = new StringBuilder();
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));

        // 创建消息处理器
        ICodingService.MessageHandler handler = new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                result.append(text);
                log.info("Understanding: {}", text);
            }

            @Override
            public void onToolCall(String toolName, String status) {
                log.info("Tool: {} - {}", toolName, status);
            }

            @Override
            public void onToolResult(String content) {
                log.info("Tool Result: {}", content);
            }

            @Override
            public void onTaskFinish(String stopReason) {
                log.info("Understanding finished: {}", stopReason);
            }

            @Override
            public void onError(Throwable error) {
                log.error("Understanding error", error);
                result.append("\n[理解过程中出现错误: ").append(error.getMessage()).append("]");
            }

            @Override
            public void onComplete() {
                log.info("Understanding completed");
            }
        };

        String taskPrompt = buildUnderstandingPrompt(userRequirement);
        codingService.executeTask(taskPrompt, tempDir, handler);

        return result.toString();
    }

    /**
     * 生成代码
     */
    public void generateCode(
            String irContent,
            Path outputPath,
            Consumer<String> logConsumer
    ) {
        log.info("Generating code to: {}", outputPath);

        // 创建日志处理器
        ICodingService.MessageHandler handler = new ICodingService.MessageHandler() {
            @Override
            public void onAssistantMessage(String text) {
                log("Assistant: " + text);
            }

            @Override
            public void onToolCall(String toolName, String status) {
                log("Tool: " + toolName + " - " + status);
            }

            @Override
            public void onToolResult(String content) {
                log("Tool Result: " + content);
            }

            @Override
            public void onTaskFinish(String stopReason) {
                log("Task finished: " + stopReason);
            }

            @Override
            public void onError(Throwable error) {
                log("ERROR: " + error.getMessage());
            }

            @Override
            public void onComplete() {
                log("Task completed");
            }

            private void log(String message) {
                if (logConsumer != null) {
                    logConsumer.accept(message);
                } else {
                    System.out.println(message);
                }
            }
        };

        String taskPrompt = buildCodeGenerationPrompt(irContent, outputPath);
        codingService.executeTask(taskPrompt, outputPath, handler, codeTaskTimeoutMillis);

        log.info("Code generation completed");
    }

    /**
     * 构建需求理解提示词
     */
    private String buildUnderstandingPrompt(String userRequirement) {
        return renderPromptTemplate(
                understandingPromptTemplateLocation,
                Map.of(
                        "USER_REQUIREMENT", safeTemplateValue(userRequirement)
                )
        );
    }

    /**
     * 构建代码生成提示词
     */
    private String buildCodeGenerationPrompt(String irContent, Path outputPath) {
        return renderPromptTemplate(
                codeGenerationPromptTemplateLocation,
                Map.of(
                        "IR_CONTENT", safeTemplateValue(irContent),
                        "OUTPUT_PATH", outputPath.toAbsolutePath().toString()
                )
        );
    }

    private String renderPromptTemplate(String templateLocation, Map<String, String> variables) {
        String template = loadPromptTemplateContent(templateLocation);
        String rendered = template;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                rendered = rendered.replace(placeholder, safeTemplateValue(entry.getValue()));
            }
        }
        return rendered;
    }

    private String loadPromptTemplateContent(String templateLocation) {
        Resource templateResource = resourceLoader.getResource(templateLocation);
        if (!templateResource.exists()) {
            throw new RuntimeException("Prompt template does not exist: " + templateLocation);
        }
        try (var inputStream = templateResource.getInputStream()) {
            byte[] content = inputStream.readAllBytes();
            String rendered = new String(content, StandardCharsets.UTF_8);
            if (rendered.isBlank()) {
                throw new RuntimeException("Prompt template is empty: " + templateLocation);
            }
            return rendered;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load prompt template: " + templateLocation, e);
        }
    }

    private String safeTemplateValue(String value) {
        return value == null ? "" : value.trim();
    }
}
