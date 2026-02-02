package com.aigen.studio.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreviewStatusDTO {
    private Long conversationId;
    private boolean running;
    private boolean frontendRunning;
    private boolean backendRunning;
    private Integer frontendPort;
    private Integer backendPort;
    private String frontendUrl;
    private String backendUrl;
    private String message;
}
