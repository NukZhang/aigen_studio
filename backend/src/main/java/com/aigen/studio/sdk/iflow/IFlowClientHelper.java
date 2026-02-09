package com.aigen.studio.sdk.iflow;

import cn.iflow.sdk.core.IFlowClient;
import cn.iflow.sdk.types.config.IFlowOptions;
import cn.iflow.sdk.types.enums.ApprovalMode;
import cn.iflow.sdk.types.enums.PermissionMode;
import cn.iflow.sdk.types.messages.*;
import com.aigen.studio.sdk.ICodingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * iFlow SDK 客户端辅助类
 * 提供统一的 iFlow 客户端创建、配置和消息处理逻辑
 * 实现 ICodingService 接口，支持 iFlow SDK
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IFlowClientHelper implements ICodingService {

    @Value("${iflow.sdk.api-key}")
    private String iflowApiKey;

    @Value("${iflow.sdk.timeout:300000}")
    private long timeoutMillis;

    /**
     * iFlow 消息处理器接口
     */
    public interface IFlowMessageHandler {
        /**
         * 处理助手消息
         */
        void onAssistantMessage(AssistantMessage message);

        /**
         * 处理工具调用消息
         */
        void onToolCallMessage(ToolCallMessage message);

        /**
         * 处理工具结果消息
         */
        void onToolResultMessage(ToolResultMessage message);

        /**
         * 处理任务完成消息
         */
        void onTaskFinishMessage(TaskFinishMessage message);

        /**
         * 处理错误
         */
        void onError(Throwable error);

        /**
         * 处理完成
         */
        void onComplete();
    }

    /**
     * 创建 iFlow 客户端选项
     */
    public IFlowOptions createOptions(Path workDir) {
        return createOptions(workDir, timeoutMillis);
    }

    /**
     * 创建 iFlow 客户端选项（带自定义超时）
     */
    public IFlowOptions createOptions(Path workDir, long timeout) {
        Path absoluteWorkDir = workDir.toAbsolutePath();

        return IFlowOptions.builder()
                .autoStartProcess(true)
                .timeout(Duration.ofMillis(timeout))
                .permissionMode(PermissionMode.AUTO)
                .approvalMode(ApprovalMode.YOLO)
                .fileAccess(true)
                .fileReadOnly(false)
                .fileAllowedDirs(List.of(absoluteWorkDir.toString()))
                .cwd(absoluteWorkDir.toString())
                .build();
    }

    /**
     * 创建 iFlow 客户端
     */
    public IFlowClient createClient(IFlowOptions options) {
        return IFlowClient.create(options);
    }

    /**
     * 创建 iFlow 客户端（使用默认配置）
     */
    public IFlowClient createClient(Path workDir) {
        return createClient(createOptions(workDir));
    }

    /**
     * 创建 iFlow 客户端（使用默认配置和自定义超时）
     */
    public IFlowClient createClient(Path workDir, long timeout) {
        return createClient(createOptions(workDir, timeout));
    }

    // ==================== ICodingService 接口实现 ====================

    

    /**
     * 执行任务（通过对话方式）
     * 实现 ICodingService 接口
     */
    @Override
    public void executeTask(String prompt, Path workDir, MessageHandler handler) {
        // 创建适配器，将 ICodingService.MessageHandler 转换为 IFlowMessageHandler
        IFlowMessageHandler flowHandler = new IFlowMessageHandler() {
            @Override
            public void onAssistantMessage(AssistantMessage message) {
                handler.onAssistantMessage(message.getChunk().getText());
            }

            @Override
            public void onToolCallMessage(ToolCallMessage message) {
                handler.onToolCall(message.getLabel(), message.getStatus().toString());
            }

            @Override
            public void onToolResultMessage(ToolResultMessage message) {
                handler.onToolResult(formatToolResultContent(message));
            }

            @Override
            public void onTaskFinishMessage(TaskFinishMessage message) {
                handler.onTaskFinish(message.getStopReason().toString());
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }

            @Override
            public void onComplete() {
                handler.onComplete();
            }
        };

        // 使用现有的 executeTask 方法
        IFlowClient client = createClient(workDir);
        executeTask(client, prompt, flowHandler);
        client.close();
    }

    String formatToolResultContent(ToolResultMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        return message.getContent().toString();
    }

    

    // ==================== ICodingService 接口实现结束 ====================

    /**
     * 执行 iFlow 任务（带自定义消息处理器）
     */
    public void executeTask(
            IFlowClient client,
            String taskPrompt,
            IFlowMessageHandler handler,
            long timeout
    ) {
        try {
            log.info("Connecting to iFlow...");
            client.connect().block();

            log.info("Sending task to iFlow...");
            client.sendMessage(taskPrompt).block();

            log.info("Receiving messages from iFlow...");
            client.receiveMessages()
                    .doOnNext(message -> {
                        if (message instanceof AssistantMessage) {
                            handler.onAssistantMessage((AssistantMessage) message);
                        } else if (message instanceof ToolCallMessage) {
                            handler.onToolCallMessage((ToolCallMessage) message);
                        } else if (message instanceof ToolResultMessage) {
                            handler.onToolResultMessage((ToolResultMessage) message);
                        } else if (message instanceof TaskFinishMessage) {
                            handler.onTaskFinishMessage((TaskFinishMessage) message);
                        }
                    })
                    .takeUntil(message -> message instanceof TaskFinishMessage)
                    .doOnError(handler::onError)
                    .doOnComplete(handler::onComplete)
                    .timeout(Duration.ofMillis(timeout))
                    .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                        log.warn("iFlow task timed out after {} ms", timeout);
                        handler.onError(e);
                        handler.onComplete();
                        return Flux.empty();
                    })
                    .blockLast(); // 等待流完成

        } catch (Exception e) {
            log.error("iFlow client error", e);
            handler.onError(e);
            throw new RuntimeException("iFlow task execution failed", e);
        }
    }

    /**
     * 执行 iFlow 任务（使用默认超时）
     */
    public void executeTask(
            IFlowClient client,
            String taskPrompt,
            IFlowMessageHandler handler
    ) {
        executeTask(client, taskPrompt, handler, timeoutMillis);
    }

    /**
     * 简化的日志处理器
     */
    public static class LoggingHandler implements IFlowMessageHandler {
        private final Consumer<String> logConsumer;

        public LoggingHandler(Consumer<String> logConsumer) {
            this.logConsumer = logConsumer;
        }

        @Override
        public void onAssistantMessage(AssistantMessage message) {
            String text = message.getChunk().getText();
            log("Assistant: " + text);
        }

        @Override
        public void onToolCallMessage(ToolCallMessage message) {
            log("Tool: " + message.getLabel() + " - " + message.getStatus());
        }

        @Override
        public void onToolResultMessage(ToolResultMessage message) {
            log("Tool Result: " + message.getContent());
        }

        @Override
        public void onTaskFinishMessage(TaskFinishMessage message) {
            log("Task finished: " + message.getStopReason());
        }

        @Override
        public void onError(Throwable error) {
            log("ERROR: " + error.getMessage());
        }

        @Override
        public void onComplete() {
            log("iFlow task completed");
        }

        private void log(String message) {
            if (logConsumer != null) {
                logConsumer.accept(message);
            } else {
                System.out.println(message);
            }
        }
    }
}
