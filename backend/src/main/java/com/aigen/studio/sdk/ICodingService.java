package com.aigen.studio.sdk;

import com.aigen.studio.dto.ModelDTO;

import java.nio.file.Path;
import java.util.List;

/**
 * 编码服务接口
 * 定义与 AI SDK 交互的标准接口，支持多种 SDK 实现
 */
public interface ICodingService {

    /**
     * 获取可用的模型列表
     *
     * @return 模型列表
     */
    List<ModelDTO> getAvailableModels();

    /**
     * 执行任务（通过对话方式）
     *
     * @param prompt 任务提示词
     * @param workDir 工作目录
     * @param handler 消息处理器
     */
    void executeTask(String prompt, Path workDir, MessageHandler handler);

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