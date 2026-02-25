package com.aigen.studio.sdk;

import java.nio.file.Path;
import java.util.List;

/**
 * 编码服务接口
 * 定义与 AI SDK 交互的标准接口，支持多种 SDK 实现
 */
public interface ICodingService {

    /**
     * 执行任务（通过对话方式）
     *
     * @param prompt 任务提示词
     * @param workDir 工作目录
     * @param handler 消息处理器
     */
    void executeTask(String prompt, Path workDir, MessageHandler handler);

    /**
     * 执行任务（自定义超时时间）
     *
     * @param prompt 任务提示词
     * @param workDir 工作目录
     * @param handler 消息处理器
     * @param timeoutMillis 超时时间（毫秒）
     */
    default void executeTask(String prompt, Path workDir, MessageHandler handler, long timeoutMillis) {
        executeTask(prompt, workDir, handler);
    }

    /**
     * 消息处理器接口
     */
    interface MessageHandler {
        void onAssistantMessage(String text);
        void onToolCall(String toolName, String status);
        void onToolResult(String content);
        void onTaskFinish(String stopReason);
        void onError(Throwable error);
        void onComplete();
    }
}
