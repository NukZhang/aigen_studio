package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImplementationVerifyRequest {

    /**
     * 可选：直接指定验证是否通过
     */
    private Boolean passed;

    /**
     * 验证项明细
     */
    private List<VerificationItem> verifications;

    /**
     * 验证产物引用
     */
    private List<String> artifacts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationItem {
        private String cmd;
        private String status;
        private String summary;
        private String logsRef;
    }
}
