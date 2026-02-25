package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新对话标题请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationTitleRequest {
    private String projectName;
}
