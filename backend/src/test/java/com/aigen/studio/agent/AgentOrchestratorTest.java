package com.aigen.studio.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorTest {

    @Mock
    private SupervisorAgent supervisorAgent;

    @Mock
    private AnalystAgent analystAgent;

    @Mock
    private DesignerAgent designerAgent;

    @Mock
    private DeveloperAgent developerAgent;

    @InjectMocks
    private AgentOrchestrator orchestrator;

    @Test
    void executesSupervisorAndWorkersUntilEnd() {
        when(supervisorAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            if (state.getAnalysisResult().isEmpty()) {
                state.setNextAgent("analyst");
            } else if (state.getUiDesign().isBlank()) {
                state.setNextAgent("designer");
            } else if (state.getGeneratedCode().isBlank()) {
                state.setNextAgent("developer");
            } else {
                state.setNextAgent("end");
            }
            return state;
        });

        when(analystAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setAnalysisResult(Map.of("summary", "analysis-done"));
            state.setNextAgent("supervisor");
            return state;
        });

        when(designerAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setUiDesign("ui-design");
            state.setNextAgent("supervisor");
            return state;
        });

        when(developerAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setGeneratedCode("generated-code");
            state.setNextAgent("supervisor");
            return state;
        });

        AgentState result = orchestrator.execute("请实现待办应用");

        assertEquals("generated-code", result.getGeneratedCode());
        assertEquals("end", result.getNextAgent());
        assertTrue(result.getIterations() >= 4);
        verify(supervisorAgent, atLeastOnce()).execute(any());
        verify(analystAgent).execute(any());
        verify(designerAgent).execute(any());
        verify(developerAgent).execute(any());
    }

    @Test
    void forcesWorkflowToEndWhenMaxIterationReached() {
        when(supervisorAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setNextAgent("analyst");
            return state;
        });
        when(analystAgent.execute(any())).thenAnswer(invocation -> {
            AgentState state = invocation.getArgument(0);
            state.setNextAgent("supervisor");
            return state;
        });

        AgentState result = orchestrator.execute("需求A", 2);

        assertEquals("end", result.getNextAgent());
        assertEquals(2, result.getIterations());
        assertTrue(result.getMessages().stream().anyMatch(msg -> msg.contains("最大迭代")));
    }
}
