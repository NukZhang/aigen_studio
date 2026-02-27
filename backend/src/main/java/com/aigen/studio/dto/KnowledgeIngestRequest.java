package com.aigen.studio.dto;

import lombok.Data;

import java.util.Map;

@Data
public class KnowledgeIngestRequest {
    private String content;
    private Map<String, Object> metadata;
}
