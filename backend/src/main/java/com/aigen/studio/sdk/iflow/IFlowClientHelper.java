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
import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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

    private static final long DEFAULT_FIRST_RESPONSE_TIMEOUT_BUFFER_MILLIS = 60000L;
    private static final Semaphore IFLOW_EXECUTION_SEMAPHORE = new Semaphore(1, true);

    @Value("${iflow.sdk.api-key}")
    private String iflowApiKey;

    @Value("${iflow.sdk.timeout:60000}")
    private long timeoutMillis;

    @Value("${iflow.sdk.first-response-timeout-buffer-ms:60000}")
    private long firstResponseTimeoutBufferMillis = DEFAULT_FIRST_RESPONSE_TIMEOUT_BUFFER_MILLIS;

    @Value("${iflow.sdk.skill-inactivity-timeout-ms:180000}")
    private long skillInactivityTimeoutMillis = 180000L;

    @Value("${iflow.sdk.tool-inactivity-timeout-ms:180000}")
    private long toolInactivityTimeoutMillis = 180000L;

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
        IFlowMessageHandler flowHandler = adaptHandler(handler);
        IFlowClient client = createClient(workDir);
        try {
            executeTask(client, prompt, flowHandler);
        } finally {
            client.close();
        }
    }

    @Override
    public void executeTask(String prompt, Path workDir, MessageHandler handler, long timeoutMillis) {
        IFlowMessageHandler flowHandler = adaptHandler(handler);
        IFlowClient client = createClient(workDir, timeoutMillis);
        try {
            executeTask(client, prompt, flowHandler, timeoutMillis);
        } finally {
            client.close();
        }
    }

    String formatToolResultContent(ToolResultMessage message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        return message.getContent().toString();
    }

    private IFlowMessageHandler adaptHandler(MessageHandler handler) {
        return new IFlowMessageHandler() {
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
        boolean slotAcquired = false;
        try {
            log.info("Waiting for iFlow execution slot...");
            IFLOW_EXECUTION_SEMAPHORE.acquire();
            slotAcquired = true;
            log.info("Acquired iFlow execution slot");

            log.info("Connecting to iFlow...");
            client.connect().block();

            log.info("Sending task to iFlow...");
            client.sendMessage(taskPrompt).block();

            log.info("Receiving messages from iFlow...");
            long taskStartAt = System.currentTimeMillis();
            long firstResponseTimeout = resolveFirstResponseTimeout(timeout);
            int promptLength = taskPrompt == null ? 0 : taskPrompt.length();
            int promptHash = taskPrompt == null ? 0 : taskPrompt.hashCode();
            boolean mentionsPencilUiDesign = taskPrompt != null && taskPrompt.contains("pencil-ui-design");
            AtomicInteger rawMessages = new AtomicInteger();
            AtomicInteger taskMessages = new AtomicInteger();
            AtomicInteger nonTaskMessages = new AtomicInteger();
            AtomicInteger assistantMessages = new AtomicInteger();
            AtomicInteger toolCallMessages = new AtomicInteger();
            AtomicInteger toolResultMessages = new AtomicInteger();
            AtomicInteger taskFinishMessages = new AtomicInteger();
            AtomicLong firstTaskMessageAt = new AtomicLong(-1L);
            AtomicLong lastTaskMessageAt = new AtomicLong(-1L);
            AtomicReference<String> firstTaskMessageType = new AtomicReference<>(null);
            AtomicReference<String> lastTaskMessageType = new AtomicReference<>(null);
            AtomicBoolean toolActivityDetected = new AtomicBoolean(false);
            AtomicBoolean skillInvocationDetected = new AtomicBoolean(false);

            log.info(
                    "iFlow task metadata: promptLength={}, promptHash={}, mentionsPencilUiDesign={}, firstResponseTimeoutMs={}, inactivityTimeoutMs={}, toolInactivityTimeoutMs={}, skillInactivityTimeoutMs={}",
                    promptLength, promptHash, mentionsPencilUiDesign, firstResponseTimeout, timeout, toolInactivityTimeoutMillis, skillInactivityTimeoutMillis
            );

            client.receiveMessages()
                    .doOnNext(message -> {
                        rawMessages.incrementAndGet();

                        if (!isTaskMessage(message)) {
                            nonTaskMessages.incrementAndGet();
                            return;
                        }

                        taskMessages.incrementAndGet();
                        String messageType = message.getClass().getSimpleName();
                        long now = System.currentTimeMillis();
                        if (firstTaskMessageAt.compareAndSet(-1L, now)) {
                            firstTaskMessageType.compareAndSet(null, messageType);
                        }
                        lastTaskMessageAt.set(now);
                        lastTaskMessageType.set(messageType);

                        if (message instanceof AssistantMessage) {
                            assistantMessages.incrementAndGet();
                            handler.onAssistantMessage((AssistantMessage) message);
                        } else if (message instanceof ToolCallMessage) {
                            toolCallMessages.incrementAndGet();
                            toolActivityDetected.set(true);
                            ToolCallMessage toolCallMessage = (ToolCallMessage) message;
                            if (isSkillLaunchToolCall(toolCallMessage)) {
                                skillInvocationDetected.set(true);
                            }
                            handler.onToolCallMessage(toolCallMessage);
                        } else if (message instanceof ToolResultMessage) {
                            toolResultMessages.incrementAndGet();
                            toolActivityDetected.set(true);
                            handler.onToolResultMessage((ToolResultMessage) message);
                        } else if (message instanceof TaskFinishMessage) {
                            taskFinishMessages.incrementAndGet();
                            handler.onTaskFinishMessage((TaskFinishMessage) message);
                        }
                    })
                    .filter(this::isTaskMessage)
                    .takeUntil(message -> message instanceof TaskFinishMessage)
                    .doOnError(handler::onError)
                    .doOnComplete(handler::onComplete)
                    .timeout(
                            Mono.delay(Duration.ofMillis(firstResponseTimeout)),
                            ignored -> Mono.delay(Duration.ofMillis(resolveInactivityTimeout(timeout, toolActivityDetected.get(), skillInvocationDetected.get())))
                    )
                    .onErrorResume(java.util.concurrent.TimeoutException.class, e -> {
                        long now = System.currentTimeMillis();
                        String phase = describeTimeoutPhase(taskMessages.get(), firstTaskMessageAt.get(), lastTaskMessageAt.get());
                        long elapsed = now - taskStartAt;
                        long firstTaskMessageDelay = firstTaskMessageAt.get() > 0 ? firstTaskMessageAt.get() - taskStartAt : -1L;
                        log.warn(
                                "iFlow task timed out: phase={}, elapsedMs={}, firstTaskMessageDelayMs={}, taskMessages={}, rawMessages={}, nonTaskMessages={}, assistantMessages={}, toolCallMessages={}, toolResultMessages={}, taskFinishMessages={}, firstTaskMessageType={}, lastTaskMessageType={}, toolActivityDetected={}, skillInvocationDetected={}, promptLength={}, promptHash={}, mentionsPencilUiDesign={}",
                                phase,
                                elapsed,
                                firstTaskMessageDelay,
                                taskMessages.get(),
                                rawMessages.get(),
                                nonTaskMessages.get(),
                                assistantMessages.get(),
                                toolCallMessages.get(),
                                toolResultMessages.get(),
                                taskFinishMessages.get(),
                                firstTaskMessageType.get(),
                                lastTaskMessageType.get(),
                                toolActivityDetected.get(),
                                skillInvocationDetected.get(),
                                promptLength,
                                promptHash,
                                mentionsPencilUiDesign
                        );
                        handler.onError(new java.util.concurrent.TimeoutException(
                                "iFlow stream timeout phase=" + phase
                                        + ", elapsedMs=" + elapsed
                                        + ", firstTaskMessageDelayMs=" + firstTaskMessageDelay
                                        + ", taskMessages=" + taskMessages.get()
                                        + ", rawMessages=" + rawMessages.get()
                        ));
                        handler.onComplete();
                        return Flux.empty();
                    })
                    .blockLast(); // 等待流完成

            long finishedAt = System.currentTimeMillis();
            long totalElapsed = finishedAt - taskStartAt;
            long firstTaskMessageDelay = firstTaskMessageAt.get() > 0 ? firstTaskMessageAt.get() - taskStartAt : -1L;
            log.info(
                    "iFlow stream completed: elapsedMs={}, firstTaskMessageDelayMs={}, taskMessages={}, rawMessages={}, nonTaskMessages={}, assistantMessages={}, toolCallMessages={}, toolResultMessages={}, taskFinishMessages={}, firstTaskMessageType={}, lastTaskMessageType={}, toolActivityDetected={}, skillInvocationDetected={}, promptLength={}, promptHash={}, mentionsPencilUiDesign={}",
                    totalElapsed,
                    firstTaskMessageDelay,
                    taskMessages.get(),
                    rawMessages.get(),
                    nonTaskMessages.get(),
                    assistantMessages.get(),
                    toolCallMessages.get(),
                    toolResultMessages.get(),
                    taskFinishMessages.get(),
                    firstTaskMessageType.get(),
                    lastTaskMessageType.get(),
                    toolActivityDetected.get(),
                    skillInvocationDetected.get(),
                    promptLength,
                    promptHash,
                    mentionsPencilUiDesign
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for iFlow execution slot", e);
            handler.onError(e);
            throw new RuntimeException("iFlow task execution interrupted", e);
        } catch (Exception e) {
            log.error("iFlow client error", e);
            handler.onError(e);
            throw new RuntimeException("iFlow task execution failed", e);
        } finally {
            if (slotAcquired) {
                IFLOW_EXECUTION_SEMAPHORE.release();
                log.info("Released iFlow execution slot");
            }
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

    long resolveFirstResponseTimeout(long timeout) {
        long buffer = Math.max(0L, firstResponseTimeoutBufferMillis);
        if (timeout <= 0) {
            return buffer;
        }
        return timeout + buffer;
    }

    String describeTimeoutPhase(int totalMessages, long firstMessageAt, long lastMessageAt) {
        if (totalMessages <= 0 || firstMessageAt <= 0 || lastMessageAt <= 0) {
            return "first_response_timeout";
        }
        return "inactivity_timeout_after_messages";
    }

    private boolean isTaskMessage(Message message) {
        return message instanceof AssistantMessage
                || message instanceof ToolCallMessage
                || message instanceof ToolResultMessage
                || message instanceof TaskFinishMessage;
    }

    private boolean isSkillLaunchToolCall(ToolCallMessage message) {
        if (message == null || message.getLabel() == null) {
            return false;
        }
        String label = message.getLabel().toLowerCase();
        return label.contains("launch skill")
                || label.contains("pencil-ui-design")
                || label.contains("skill:");
    }

    private long resolveInactivityTimeout(long timeout, boolean toolActivityDetected, boolean skillInvocationDetected) {
        long defaultTimeout = Math.max(1L, timeout);
        if (!toolActivityDetected && !skillInvocationDetected) {
            return defaultTimeout;
        }
        long toolTimeout = Math.max(defaultTimeout, Math.max(1L, toolInactivityTimeoutMillis));
        if (!skillInvocationDetected) {
            return toolTimeout;
        }
        return Math.max(toolTimeout, Math.max(1L, skillInactivityTimeoutMillis));
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
