package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话数据传输对象
 * 表示一个完整的对话会话，支持独立会话和基于 Job 的会话
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private Long id;                    // 对话 ID
    private String projectName;         // 项目名称
    private String status;              // 对话状态（ACTIVE, COMPLETED, CANCELLED, FAILED）
    private String stage;               // 对话阶段（NEED_INPUT, UNDERSTANDING, UNDERSTANDING_CONFIRMED, CODE_GENERATING, SERVICE_STARTING, PREVIEWING, COMPLETED, FAILED）
    private String userRequirement;     // 用户输入的需求描述
    private String aiUnderstanding;     // AI 理解的需求
    private Boolean understandingConfirmed;  // 用户是否确认理解内容
    private String generatedCodePath;   // 生成的代码路径
    private String serviceStatus;       // 服务状态
    private String previewUrl;          // 预览 URL
    private String uiPrototypePath;     // UI 原型路径
    private String uiPrototypeContent;  // UI 原型内容（HTML）
    private Boolean uiConfirmed;        // 用户是否确认 UI 设计
    private String errorMessage;        // 错误信息
    private LocalDateTime createdAt;    // 创建时间
    private LocalDateTime updatedAt;    // 更新时间
    private List<MessageDTO> messages;  // 消息列表
}
