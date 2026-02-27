package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerAgentsTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    @InjectMocks
    private AnalystAgent analystAgent;

    @InjectMocks
    private DesignerAgent designerAgent;

    @InjectMocks
    private DeveloperAgent developerAgent;

    @Test
    void analystProducesAnalysisAndReturnsToSupervisor() {
        AgentState state = AgentState.initial("实现任务看板系统");
        when(chatLanguageModel.generate(anyString())).thenReturn("核心功能: 看板、任务状态、筛选");

        AgentState result = analystAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertFalse(result.getAnalysisResult().isEmpty());
        assertTrue(result.getAnalysisResult().containsKey("summary"));
    }

    @Test
    void analystFallsBackWhenModelFails() {
        AgentState state = AgentState.initial("实现任务看板系统");
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("invalid_api_key"));

        AgentState result = analystAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertTrue(result.getAnalysisResult().containsKey("summary"));
        assertFalse(String.valueOf(result.getAnalysisResult().get("summary")).isBlank());
        assertTrue(result.getMessages().stream().anyMatch(message -> message.contains("兜底")));
    }

    @Test
    void designerProducesUiPlanAndReturnsToSupervisor() {
        AgentState state = AgentState.initial("实现任务看板系统");
        state.setAnalysisResult(Map.of("summary", "完成分析"));
        when(chatLanguageModel.generate(anyString())).thenReturn("页面包含列表页、详情页和统计卡片");

        AgentState result = designerAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertEquals("页面包含列表页、详情页和统计卡片", result.getUiDesign());
    }

    @Test
    void designerFallsBackWhenModelFails() {
        AgentState state = AgentState.initial("实现任务看板系统");
        state.setAnalysisResult(Map.of("summary", "完成分析"));
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("invalid_api_key"));

        AgentState result = designerAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertFalse(result.getUiDesign().isBlank());
        assertTrue(result.getMessages().stream().anyMatch(message -> message.contains("兜底")));
    }

    @Test
    void developerProducesImplementationAndReturnsToSupervisor() {
        AgentState state = AgentState.initial("实现任务看板系统");
        state.setAnalysisResult(Map.of("summary", "完成分析"));
        state.setUiDesign("UI 方案 A");
        when(chatLanguageModel.generate(anyString())).thenReturn("后端 Spring Boot，前端 Vue3，分模块交付");

        AgentState result = developerAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertEquals("后端 Spring Boot，前端 Vue3，分模块交付", result.getGeneratedCode());
    }

    @Test
    void developerFallsBackWhenModelTimeout() {
        AgentState state = AgentState.initial("实现任务看板系统");
        state.setAnalysisResult(Map.of("summary", "完成分析"));
        state.setUiDesign("UI 方案 A");
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException(new java.io.InterruptedIOException("timeout")));

        AgentState result = developerAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertFalse(result.getGeneratedCode().isBlank());
        assertTrue(result.getMessages().stream().anyMatch(message -> message.contains("超时")));
    }

    @Test
    void developerFallsBackWhenModelFails() {
        AgentState state = AgentState.initial("实现任务看板系统");
        state.setAnalysisResult(Map.of("summary", "完成分析"));
        state.setUiDesign("UI 方案 A");
        when(chatLanguageModel.generate(anyString()))
                .thenThrow(new RuntimeException("invalid_api_key"));

        AgentState result = developerAgent.execute(state);

        assertEquals("supervisor", result.getNextAgent());
        assertFalse(result.getGeneratedCode().isBlank());
        assertTrue(result.getMessages().stream().anyMatch(message -> message.contains("兜底")));
    }
}
