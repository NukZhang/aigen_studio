package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用数据传输对象
 * 表示 AI 调用的一个工具（如 Bash Command, Read File, Edit File）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallDTO {
    private Long id;                    // 工具调用 ID
    private String toolName;            // 工具名称（Bash Command, Read File, Edit File, Generate App Info, Image Generation 等）
    private String arguments;           // 工具参数（JSON 字符串）
    private String result;              // 工具执行结果
    private String status;              // 执行状态（success/failed/running）
    private Long executionTime;         // 执行时间（毫秒）
    private String icon;                // 工具图标（可选）
}