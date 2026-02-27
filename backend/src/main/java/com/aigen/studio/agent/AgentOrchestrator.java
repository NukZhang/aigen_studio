package com.aigen.studio.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final int DEFAULT_MAX_ITERATIONS = 10;

    private final SupervisorAgent supervisorAgent;
    private final AnalystAgent analystAgent;
    private final DesignerAgent designerAgent;
    private final DeveloperAgent developerAgent;

    public AgentState execute(String userInput) {
        return execute(userInput, DEFAULT_MAX_ITERATIONS);
    }

    public AgentState execute(String userInput, int maxIterations) {
        AgentState state = AgentState.initial(userInput);
        int limit = maxIterations <= 0 ? DEFAULT_MAX_ITERATIONS : maxIterations;

        while (!"end".equals(state.getNextAgent()) && state.getIterations() < limit) {
            state = route(state);
            state.setIterations(state.getIterations() + 1);
            log.info("Multi-agent iteration {} finished, next={}", state.getIterations(), state.getNextAgent());
        }

        if (!"end".equals(state.getNextAgent()) && state.getIterations() >= limit) {
            state.setNextAgent("end");
            state.addMessage("已达到最大迭代次数，流程自动结束。");
            state.addEvent("orchestrator", "end", "达到最大迭代次数，触发保护性收敛");
        }

        return state;
    }

    public String renderFlow(AgentState state) {
        StringBuilder flow = new StringBuilder("graph TD\n");
        if (state == null || state.getEvents() == null || state.getEvents().isEmpty()) {
            flow.append("    Start --> End");
            return flow.toString();
        }

        for (int i = 0; i < state.getEvents().size(); i++) {
            AgentEvent event = state.getEvents().get(i);
            String from = "N" + i + "[" + safe(event.getAgent()) + "]";
            String to = "N" + (i + 1) + "[" + safe(event.getNextAgent()) + "]";
            flow.append("    ").append(from)
                    .append(" -->|").append(safe(event.getDetail())).append("| ")
                    .append(to)
                    .append('\n');
        }
        return flow.toString();
    }

    private AgentState route(AgentState state) {
        if (state == null) {
            return AgentState.initial("");
        }

        return switch (state.getNextAgent()) {
            case "supervisor" -> supervisorAgent.execute(state);
            case "analyst" -> analystAgent.execute(state);
            case "designer" -> designerAgent.execute(state);
            case "developer" -> developerAgent.execute(state);
            case "end" -> state;
            default -> {
                state.setNextAgent("supervisor");
                state.addMessage("未知智能体，已回退到 supervisor。");
                state.addEvent("orchestrator", "supervisor", "检测到未知路由，执行回退");
                yield state;
            }
        };
    }

    private String safe(String text) {
        if (text == null || text.isBlank()) {
            return "unknown";
        }
        return text.replace("|", "/").replace("[", "(").replace("]", ")");
    }
}
