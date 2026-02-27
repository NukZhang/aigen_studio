package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupervisorAgent {

    private static final Set<String> ALLOWED_NEXT_AGENTS = Set.of("analyst", "designer", "developer", "end");
    private final ChatLanguageModel chatLanguageModel;

    public AgentState execute(AgentState state) {
        String heuristicDecision = decideByState(state);
        String finalDecision = heuristicDecision;

        try {
            String prompt = buildPrompt(state);
            String modelDecision = normalize(chatLanguageModel.generate(prompt));
            if (ALLOWED_NEXT_AGENTS.contains(modelDecision)) {
                finalDecision = modelDecision;
            }
        } catch (Exception e) {
            log.warn("Supervisor LLM decision failed, fallback to heuristic route", e);
        }

        state.setCurrentTask("supervisor-routing");
        state.setNextAgent(finalDecision);
        state.addMessage("Supervisor 决策下一步智能体: " + finalDecision);
        state.addEvent("supervisor", finalDecision, "根据当前状态完成任务分派");
        return state;
    }

    private String decideByState(AgentState state) {
        if (state.getAnalysisResult() == null || state.getAnalysisResult().isEmpty()) {
            return "analyst";
        }
        if (isBlank(state.getUiDesign())) {
            return "designer";
        }
        if (isBlank(state.getGeneratedCode())) {
            return "developer";
        }
        return "end";
    }

    private String buildPrompt(AgentState state) {
        String analysis = state.getAnalysisResult() == null || state.getAnalysisResult().isEmpty()
                ? "无"
                : state.getAnalysisResult().toString();
        String uiDesign = isBlank(state.getUiDesign()) ? "无" : state.getUiDesign();
        String generatedCode = isBlank(state.getGeneratedCode()) ? "无" : state.getGeneratedCode();

        return """
                你是多智能体系统的主管，请只返回下一步要执行的智能体名称：
                - analyst
                - designer
                - developer
                - end

                用户需求：%s
                分析结果：%s
                UI 设计：%s
                代码方案：%s

                只输出一个单词。
                """.formatted(state.getUserInput(), analysis, uiDesign, generatedCode);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
