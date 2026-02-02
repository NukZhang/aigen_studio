package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待办事项数据传输对象
 * 表示从 IR 文档解析的任务项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TodoItemDTO {
    private Long id;                    // 待办事项 ID
    private String title;               // 任务标题
    private String description;         // 任务描述
    private String status;              // 状态（pending/in_progress/completed）
    private Integer order;              // 排序
    private String moduleName;          // 所属模块（如 frontend, backend）
    private String featureName;         // 所属功能
}