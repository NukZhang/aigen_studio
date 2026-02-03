package com.aigen.studio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 对话实体
 * 支持独立的 AI 对话会话，不依赖 Job
 */
@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 项目名称
     */
    @Column(name = "project_name", nullable = false)
    private String projectName;

    /**
     * 对话状态
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStatus status;

    /**
     * 对话阶段
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStage stage;

    /**
     * 用户输入的需求描述
     */
    @Column(name = "user_requirement", columnDefinition = "TEXT")
    private String userRequirement;

    /**
     * AI 理解的需求（IR 格式或其他格式）
     */
    @Column(name = "ai_understanding", columnDefinition = "TEXT")
    private String aiUnderstanding;

    /**
     * 用户是否确认理解内容
     */
    @Column(name = "understanding_confirmed")
    private Boolean understandingConfirmed;

    /**
     * 生成的代码路径
     */
    @Column(name = "generated_code_path")
    private String generatedCodePath;

    /**
     * 服务状态
     */
    @Column(name = "service_status")
    private String serviceStatus;

    /**
     * 预览 URL
     */
    @Column(name = "preview_url")
    private String previewUrl;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 创建人
     */
    @Column(name = "created_by")
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 对话状态枚举
     */
    public enum ConversationStatus {
        /**
         * 活跃中
         */
        ACTIVE,

        /**
         * 已完成
         */
        COMPLETED,

        /**
         * 已取消
         */
        CANCELLED,

        /**
         * 失败
         */
        FAILED
    }

    /**
     * 初始化一个新的对话
     */
    public static Conversation createNew(String projectName, String userRequirement, String createdBy) {
        Conversation conversation = new Conversation();
        conversation.setProjectName(projectName);
        conversation.setUserRequirement(userRequirement);
        conversation.setCreatedBy(createdBy);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStage(ConversationStage.NEED_INPUT);
        conversation.setUnderstandingConfirmed(false);
        return conversation;
    }
}
