package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupervisorAgentTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @InjectMocks
    private SupervisorAgent supervisorAgent;

    @Test
    void acceptsValidModelDecision() {
        AgentState state = AgentState.initial("做一个电商管理后台");
        state.setAnalysisResult(Map.of("summary", "分析完成"));

        when(chatLanguageModel.generate(anyString())).thenReturn("designer");

        AgentState result = supervisorAgent.execute(state);

        assertEquals("designer", result.getNextAgent());
    }

    @Test
    void fallsBackToHeuristicDecisionWhenModelResponseInvalid() {
        AgentState state = AgentState.initial("做一个博客系统");

        when(chatLanguageModel.generate(anyString())).thenReturn("random-agent");

        AgentState result = supervisorAgent.execute(state);

        assertEquals("analyst", result.getNextAgent());
    }
}
