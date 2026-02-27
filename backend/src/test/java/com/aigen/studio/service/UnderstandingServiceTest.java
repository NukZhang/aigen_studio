package com.aigen.studio.service;

import com.aigen.studio.config.AgentProperties;
import com.aigen.studio.rag.RAGService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnderstandingServiceTest {

    @Mock
    private PromptTaskService promptTaskService;

    @Mock
    private RAGService ragService;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @Mock
    private MultiAgentService multiAgentService;

    @Mock
    private AgentProperties agentProperties;

    @InjectMocks
    private UnderstandingService understandingService;

    @Test
    void routesToRagWhenLangChainAndRagEnabled() {
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableMultiAgent()).thenReturn(false);
        when(agentProperties.isEnableRag()).thenReturn(true);
        when(ragService.generateWithRAG("订单系统", 88L)).thenReturn("rag-result");

        String result = understandingService.understandRequirement("订单系统", 88L);

        assertEquals("rag-result", result);
        verify(chatLanguageModel, never()).generate(anyString());
    }

    @Test
    void fallsBackToLangChainWhenRagFails() {
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableMultiAgent()).thenReturn(false);
        when(agentProperties.isEnableRag()).thenReturn(true);
        when(ragService.generateWithRAG("需求", 9L)).thenThrow(new RuntimeException("rag down"));
        when(chatLanguageModel.generate(anyString())).thenReturn("langchain-result");

        String result = understandingService.understandRequirement("需求", 9L);

        assertEquals("langchain-result", result);
        verify(chatLanguageModel).generate(anyString());
    }

    @Test
    void fallsBackToIflowWhenLangChainFails() {
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableMultiAgent()).thenReturn(false);
        when(agentProperties.isEnableRag()).thenReturn(false);
        when(chatLanguageModel.generate(anyString())).thenThrow(new RuntimeException("chat down"));
        when(promptTaskService.understandRequirement("需求A")).thenReturn("iflow-result");

        String result = understandingService.understandRequirement("需求A", 1L);

        assertEquals("iflow-result", result);
        verify(promptTaskService).understandRequirement("需求A");
    }

    @Test
    void usesIflowWithProgressCallbackWhenLangChainDisabled() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);

        when(agentProperties.isUseLangChain()).thenReturn(false);
        doAnswer(invocation -> {
            java.util.function.Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("chunk");
            return "iflow-stream";
        }).when(promptTaskService).understandRequirement(anyString(), any());

        String result = understandingService.understandRequirement(
                "需求B",
                2L,
                chunk -> callbackCalled.set("chunk".equals(chunk))
        );

        assertEquals("iflow-stream", result);
        assertTrue(callbackCalled.get());
    }

    @Test
    void routesToMultiAgentWhenEnabled() {
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableMultiAgent()).thenReturn(true);
        when(multiAgentService.understandRequirement("需求C", 11L, null)).thenReturn("multi-agent-result");

        String result = understandingService.understandRequirement("需求C", 11L, null);

        assertEquals("multi-agent-result", result);
        verify(ragService, never()).generateWithRAG(anyString(), any());
        verify(chatLanguageModel, never()).generate(anyString());
    }

    @Test
    void fallsBackToRagWhenMultiAgentFails() {
        when(agentProperties.isUseLangChain()).thenReturn(true);
        when(agentProperties.isEnableMultiAgent()).thenReturn(true);
        when(agentProperties.isEnableRag()).thenReturn(true);
        when(multiAgentService.understandRequirement("需求D", 12L, null))
                .thenThrow(new RuntimeException("multi-agent unavailable"));
        when(ragService.generateWithRAG("需求D", 12L)).thenReturn("rag-fallback-result");

        String result = understandingService.understandRequirement("需求D", 12L, null);

        assertEquals("rag-fallback-result", result);
        verify(ragService).generateWithRAG("需求D", 12L);
    }
}
