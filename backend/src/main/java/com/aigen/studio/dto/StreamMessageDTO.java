package com.aigen.studio.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式消息 DTO
 * 用于 WebSocket 流式响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamMessageDTO {
    /**
     * 消息 ID
     */
    private String id;

    /**
     * 消息类型: text/tool/result/error/finish
     */
    private String type;

    /**
     * 消息内容（文本内容）
     */
    private String content;

    /**
     * 工具调用信息（type=tool 时）
     */
    private ToolCallInfo toolCall;

    /**
     * 工具结果信息（type=result 时）
     */
    private ToolResultInfo toolResult;

    /**
     * 错误信息（type=error 时）
     */
    private String error;

    /**
     * 是否完成（type=finish 时）
     */
    private Boolean finished;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallInfo {
        private String toolName;
        private String arguments;
        private String status; // running/success/failed
    }

    /**
     * 工具结果信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolResultInfo {
        private String toolName;
        private String result;
        private String status; // success/failed
    }

    /**
     * 创建文本消息
     */
    public static StreamMessageDTO text(String content) {
        return StreamMessageDTO.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("text")
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建工具调用消息
     */
    public static StreamMessageDTO toolCall(String toolName, String arguments) {
        return StreamMessageDTO.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("tool")
                .toolCall(ToolCallInfo.builder()
                        .toolName(toolName)
                        .arguments(arguments)
                        .status("running")
                        .build())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建工具结果消息
     */
    public static StreamMessageDTO toolResult(String toolName, String result, String status) {
        return StreamMessageDTO.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("result")
                .toolResult(ToolResultInfo.builder()
                        .toolName(toolName)
                        .result(result)
                        .status(status)
                        .build())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建错误消息
     */
    public static StreamMessageDTO error(String error) {
        return StreamMessageDTO.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("error")
                .error(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建完成消息
     */
    public static StreamMessageDTO finish() {
        return StreamMessageDTO.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type("finish")
                .finished(true)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
