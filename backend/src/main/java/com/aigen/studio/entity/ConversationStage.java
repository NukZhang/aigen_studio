package com.aigen.studio.entity;

/**
 * 对话阶段状态枚举
 */
public enum ConversationStage {
    /**
     * 需求输入阶段
     */
    NEED_INPUT,

    /**
     * AI 理解需求阶段
     */
    UNDERSTANDING,

    /**
     * 需求理解确认阶段（等待用户确认）
     */
    UNDERSTANDING_CONFIRMED,

    /**
     * 代码生成阶段
     */
    CODE_GENERATING,

    /**
     * 代码生成完成，等待确认启动
     */
    READY_TO_START,

    /**
     * 服务启动阶段
     */
    SERVICE_STARTING,

    /**
     * 预览阶段
     */
    PREVIEWING,

    /**
     * 对话完成
     */
    COMPLETED,

    /**
     * 对话失败
     */
    FAILED
}
