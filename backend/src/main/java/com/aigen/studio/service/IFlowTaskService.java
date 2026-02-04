package com.aigen.studio.service;

import cn.iflow.sdk.core.IFlowClient;
import cn.iflow.sdk.types.messages.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * iFlow 任务服务
 * 提供具体的任务执行逻辑，如需求理解、代码生成等
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IFlowTaskService {

    private final IFlowClientHelper clientHelper;

    /**
     * 理解需求
     */
    public String understandRequirement(String userRequirement) {
        log.info("Understanding requirement: {}", userRequirement);

        StringBuilder result = new StringBuilder();

        long timeout = 300000; // 5 分钟超时
        IFlowClient client = clientHelper.createClient(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir")),
                timeout
        );

        IFlowClientHelper.IFlowMessageHandler handler = new IFlowClientHelper.IFlowMessageHandler() {
            @Override
            public void onAssistantMessage(AssistantMessage message) {
                String text = message.getChunk().getText();
                result.append(text);
                log.info("Understanding: {}", text);
            }

            @Override
            public void onToolCallMessage(ToolCallMessage message) {
                log.info("Tool: {}", message.getLabel());
            }

            @Override
            public void onToolResultMessage(ToolResultMessage message) {
                log.info("Tool Result: {}", message.getContent());
            }

            @Override
            public void onTaskFinishMessage(TaskFinishMessage message) {
                log.info("Understanding finished: {}", message.getStopReason());
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
        clientHelper.executeTask(client, taskPrompt, handler, timeout);

        return result.toString();
    }

    /**
     * 生成代码
     */
    public void generateCode(
            String irContent,
            java.nio.file.Path outputPath,
            Consumer<String> logConsumer
    ) {
        log.info("Generating code to: {}", outputPath);

        long timeout = 300000; // 5 分钟超时
        IFlowClient client = clientHelper.createClient(outputPath, timeout);

        IFlowClientHelper.IFlowMessageHandler handler = new IFlowClientHelper.LoggingHandler(logConsumer);

        String taskPrompt = buildCodeGenerationPrompt(irContent, outputPath);
        clientHelper.executeTask(client, taskPrompt, handler, timeout);

        log.info("Code generation completed");
    }

    /**
     * 构建需求理解提示词
     */
    private String buildUnderstandingPrompt(String userRequirement) {
        return String.format("""
            请理解以下用户需求，并以结构化的方式总结：

            用户需求：
            %s

            请以以下格式返回理解结果：
            1. **项目目标**：简要描述项目的目标和用途
            2. **核心功能**：列出 3-5 个核心功能点
            3. **技术栈**：推荐合适的前后端技术栈
            4. **项目结构**：建议的项目结构
            5. **其他说明**：其他需要注意的事项

            只返回理解结果，不要进行代码生成。
            """, userRequirement);
    }

    /**
     * 构建代码生成提示词
     */
    private String buildCodeGenerationPrompt(String irContent, java.nio.file.Path outputPath) {
        return String.format("""
            请根据以下 IR 配置生成完整的代码项目：

            IR 配置：
            %s

            要求：
            1. 在当前工作目录生成完整的项目结构
            2. 生成前端 Vue 3 项目（如果包含 frontend 模块）
            3. 生成后端 Spring Boot 项目（如果包含 backend 模块）
            4. 生成 OpenAPI 规范文件 openapi.yaml
            5. 生成 TypeScript SDK（如果需要）
            6. 确保所有代码都是完整的、可运行的
            7. 添加必要的配置文件和说明文档
            8. 如果包含 frontend 模块，必须生成最小可运行的 Vite + Vue3 前端骨架：
               - frontend/index.html（含 #app 且引用 /src/main.ts）
               - frontend/src/main.ts（创建并挂载 App）
               - frontend/src/App.vue（最小根组件即可）
               - frontend/vite.config.(ts|js)（含 Vue 插件；路由需 createWebHistory(import.meta.env.BASE_URL)）
               - frontend/package.json（含 dev/build/preview 脚本和依赖）
            9. 若暂时没有业务页面，也必须生成上述入口文件，不要只创建目录或空 src。

            输出目录：%s

            请开始生成代码，并详细说明每个步骤。
            """, irContent, outputPath.toAbsolutePath());
    }
}
