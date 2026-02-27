package com.aigen.studio.service;

import com.aigen.studio.config.AgentProperties;
import com.aigen.studio.rag.RAGService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class UnderstandingService {

    private final PromptTaskService promptTaskService;
    private final RAGService ragService;
    private final ChatLanguageModel chatLanguageModel;
    private final MultiAgentService multiAgentService;
    private final AgentProperties agentProperties;

    public String understandRequirement(String requirement) {
        return understandRequirement(requirement, null, null);
    }

    public String understandRequirement(String requirement, Long conversationId) {
        return understandRequirement(requirement, conversationId, null);
    }

    public String understandRequirement(String requirement, Long conversationId, Consumer<String> progressConsumer) {
        if (agentProperties.isUseLangChain() && agentProperties.isEnableMultiAgent()) {
            try {
                return multiAgentService.understandRequirement(requirement, conversationId, progressConsumer);
            } catch (Exception e) {
                log.warn("Multi-agent understanding failed, falling back to RAG/LangChain/iFlow", e);
            }
        }

        if (agentProperties.isUseLangChain() && agentProperties.isEnableRag()) {
            try {
                String ragResult = ragService.generateWithRAG(requirement, conversationId);
                emitProgress(progressConsumer, ragResult);
                return ragResult;
            } catch (Exception e) {
                log.warn("RAG understanding failed, falling back to LangChain/iFlow", e);
            }
        }

        if (agentProperties.isUseLangChain()) {
            try {
                String langChainResult = understandWithLangChain(requirement);
                emitProgress(progressConsumer, langChainResult);
                return langChainResult;
            } catch (Exception e) {
                log.warn("LangChain understanding failed, falling back to iFlow", e);
            }
        }

        return understandWithIFlow(requirement, progressConsumer);
    }

    private String understandWithLangChain(String requirement) {
        String prompt = """
                请分析以下需求，提取关键信息：

                %s

                请输出：
                1. 核心功能点
                2. 技术要求
                3. 澄清问题（如有）
                """.formatted(requirement);
        return chatLanguageModel.generate(prompt);
    }

    private String understandWithIFlow(String requirement, Consumer<String> progressConsumer) {
        if (progressConsumer != null) {
            return promptTaskService.understandRequirement(requirement, progressConsumer);
        }
        return promptTaskService.understandRequirement(requirement);
    }

    private void emitProgress(Consumer<String> progressConsumer, String content) {
        if (progressConsumer != null && content != null && !content.isBlank()) {
            progressConsumer.accept(content);
        }
    }
}
