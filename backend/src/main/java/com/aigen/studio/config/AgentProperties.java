package com.aigen.studio.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aigen.agent")
public class AgentProperties {

    /**
     * Whether to enable RAG-enhanced understanding.
     */
    private boolean enableRag = false;

    /**
     * Whether to enable multi-agent orchestration.
     */
    private boolean enableMultiAgent = false;

    /**
     * Whether to use LangChain-based implementations.
     */
    private boolean useLangChain = false;
}
