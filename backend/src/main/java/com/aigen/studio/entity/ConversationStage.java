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
     * 需求澄清阶段（信息不足，等待用户补充）
     */
    CLARIFYING,

    /**
     * 需求理解确认阶段（等待用户确认，内部状态，显示在"理解需求"步骤）
     */
    UNDERSTANDING_CONFIRMED,
    
    /**
     * UI 生成阶段（状态栏第3步：生成UI）
     */
    UI_GENERATING,

    /**
     * UI 已生成，等待确认
     */
    UI_READY,
    
    /**
     * UI 已确认（内部状态，显示在"生成UI"步骤）
     */
    UI_CONFIRMED,
    
    /**
     * 代码生成阶段（状态栏第4步：生成代码）
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
