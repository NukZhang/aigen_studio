package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 确认理解请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmUnderstandingRequest {
    /**
     * 是否确认
     */
    private Boolean confirmed;

    /**
     * 用户反馈（如果确认但需要修改）
     */
    private String feedback;
}