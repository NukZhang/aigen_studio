package com.aigen.studio.service;

import com.aigen.studio.agent.AgentOrchestrator;
import com.aigen.studio.agent.AgentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiAgentServiceTest {

    @Mock
    private AgentOrchestrator agentOrchestrator;

    @Mock
    private Executor taskExecutor;

    @InjectMocks
    private MultiAgentService multiAgentService;

    @Test
    void buildsReadableUnderstandingResultAndStreamsTrace() {
        AgentState state = AgentState.initial("实现 CRM 系统");
        state.setAnalysisResult(Map.of("summary", "需求包含客户管理和线索跟进"));
        state.setUiDesign("双栏布局，左侧导航右侧详情");
        state.setGeneratedCode("后端 Java + 前端 Vue3");
        state.setNextAgent("end");
        state.addEvent("supervisor", "analyst", "拆解任务");
        state.addEvent("analyst", "supervisor", "分析完成");

        when(agentOrchestrator.execute("实现 CRM 系统")).thenReturn(state);

        List<String> progress = new ArrayList<>();
        String output = multiAgentService.understandRequirement("实现 CRM 系统", 7L, progress::add);

        assertTrue(output.contains("需求包含客户管理"));
        assertTrue(output.contains("双栏布局"));
        assertTrue(output.contains("Java + 前端 Vue3"));
        assertTrue(progress.stream().anyMatch(item -> item.contains("supervisor")));
    }

    @Test
    void rendersMermaidFlowFromExecutionState() {
        AgentState state = AgentState.initial("需求A");
        state.addEvent("supervisor", "analyst", "任务拆解");
        state.addEvent("analyst", "supervisor", "分析完成");

        String mermaid = multiAgentService.renderFlow(state);

        assertTrue(mermaid.contains("graph TD"));
        assertTrue(mermaid.contains("supervisor"));
        assertTrue(mermaid.contains("analyst"));
        assertEquals(2, state.getEvents().size());
    }
}
