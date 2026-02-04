package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelDTO {
    /**
     * 模型ID
     */
    private String id;

    /**
     * 模型名称
     */
    private String name;

    /**
     * 模型描述
     */
    private String description;

    /**
     * 是否为默认模型
     */
    private Boolean isDefault;

    /**
     * 模型类型
     */
    private String type;
}