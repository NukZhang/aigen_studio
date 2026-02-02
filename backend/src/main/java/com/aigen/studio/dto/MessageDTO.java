package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 消息数据传输对象
 * 表示对话中的一条消息（用户消息或 AI 回复）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;                    // 消息 ID
    private String role;                // 角色（user/assistant）
    private String content;             // 消息内容（支持 Markdown）
    private LocalDateTime timestamp;    // 时间戳
    private List<ToolCallDTO> toolCalls; // 工具调用列表（仅 AI 消息）
    private String senderName;          // 发送者名称（user: 用户名, assistant: AI 开发者）
    private String senderAvatar;        // 发送者头像 URL
}