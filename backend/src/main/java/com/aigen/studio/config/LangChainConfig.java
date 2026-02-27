package com.aigen.studio.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Keep LangChain4j wiring centralized while letting the official starter create model beans.
 * 
 * 支持混合配置模式：
 * - Chat 模型使用 iFlow 平台 (https://apis.iflow.cn/v1)
 * - Embedding 模型使用 Jina (https://api.jina.ai/v1)
 * 
 * 原因：iFlow 平台目前不支持 /v1/embeddings API
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class LangChainConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url:https://apis.iflow.cn/v1}")
    private String chatBaseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name:qwen3-max}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.embedding-model.base-url:https://api.jina.ai/v1}")
    private String embeddingBaseUrl;

    @Value("${langchain4j.open-ai.embedding-model.model-name:jina-embeddings-v2-base-zh}")
    private String embeddingModelName;

    @PostConstruct
    public void logConfiguredModels() {
        log.info("LangChain4j configured (混合模式). chatModel={} @ {}, embeddingModel={} @ {}",
                chatModelName, chatBaseUrl, embeddingModelName, embeddingBaseUrl);
    }
}
