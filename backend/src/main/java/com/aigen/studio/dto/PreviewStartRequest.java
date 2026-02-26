package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewStartRequest {
    /**
     * 绑定的 evidence 引用（通常为 manifest 路径）
     */
    private String evidenceRef;
}
