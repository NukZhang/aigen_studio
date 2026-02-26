package com.aigen.studio.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Keep LangChain4j wiring centralized while letting the official starter create model beans.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class LangChainConfig {

    @Value("${langchain4j.open-ai.chat-model.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String chatBaseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name:qwen-plus}")
    private String chatModelName;

    @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-v2}")
    private String embeddingModelName;

    @PostConstruct
    public void logConfiguredModels() {
        log.info("LangChain4j configured. chatModel={}, embeddingModel={}, baseUrl={}",
                chatModelName, embeddingModelName, chatBaseUrl);
    }
}
