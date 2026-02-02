package com.aigen.studio.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 消息实体
 * 用于存储独立对话的消息
 */
@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属对话 ID
     */
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /**
     * 关联的需求 ID
     */
    @Column(name = "requirement_id")
    private Long requirementId;

    /**
     * 角色类型
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    /**
     * 发送者名称
     */
    @Column(name = "sender_name", nullable = false)
    private String senderName;

    /**
     * 消息内容
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 创建时间
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 消息角色枚举
     */
    public enum MessageRole {
        /**
         * 用户消息
         */
        USER,

        /**
         * AI 助手消息
         */
        ASSISTANT,

        /**
         * 系统消息
         */
        SYSTEM
    }
}