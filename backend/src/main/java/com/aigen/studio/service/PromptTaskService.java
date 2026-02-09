package com.aigen.studio.service;

import com.aigen.studio.sdk.ICodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
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
        codingService.executeTask(taskPrompt, outputPath, handler);

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
    private String buildCodeGenerationPrompt(String irContent, Path outputPath) {
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
            10. 后端使用 Spring Boot 3.x 时，必须使用 jakarta.servlet.*，不要使用 javax.servlet.*。
            11. 如果使用 MyBatis-Plus，每个实体都要有对应的 Mapper 接口文件。
            12. 后端必须生成 schema.sql，且该脚本必须兼容 H2（预览默认使用 H2）：
                - 禁止使用 CREATE DATABASE、USE 等数据库级语句
                - 禁止使用 ENGINE=、CHARSET、COLLATE 等 MySQL 专属语法
                - 禁止在 CREATE TABLE 中使用 KEY/UNIQUE KEY；索引请使用 CREATE INDEX/CREATE UNIQUE INDEX 单独创建
                - 表必须使用 CREATE TABLE IF NOT EXISTS
                - 尽量使用通用数据类型（INT/BIGINT/VARCHAR/TEXT/DECIMAL/DATE/DATETIME）
            13. 如果后端使用 H2 作为默认开发数据库，application.yml 中请设置 spring.sql.init.mode=embedded。
            14. 禁止使用实体命名 Character，避免与 java.lang.Character 冲突；建议使用 PersonalityCharacter/HistoricalCharacter。
            15. 不要使用实体包通配符导入（import ...entity.*），请显式 import 需要的实体。
            16. selectCount 返回 Long，请使用 Math.toIntExact(...) 或 .intValue()。

            输出目录：%s

            请开始生成代码，并详细说明每个步骤。
            """, irContent, outputPath.toAbsolutePath());
    }
}
