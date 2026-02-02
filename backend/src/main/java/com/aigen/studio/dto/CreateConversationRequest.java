package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建对话请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {
    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 用户需求描述
     */
    private String userRequirement;

    /**
     * 创建人
     */
    private String createdBy;
}