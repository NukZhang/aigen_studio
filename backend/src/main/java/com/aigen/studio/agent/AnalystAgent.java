package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalystAgent {

    private final ChatLanguageModel chatLanguageModel;

    public AgentState execute(AgentState state) {
        String prompt = """
                你是需求分析智能体，请分析用户需求并输出简明结论：
                - 核心功能
                - 技术约束
                - 风险点

                用户需求：
                %s
                """.formatted(state.getUserInput());

        AgentModelRunner.ModelExecutionResult result = AgentModelRunner.generateWithHeartbeat(
                "Analyst",
                chatLanguageModel,
                prompt,
                () -> buildFallbackAnalysis(state.getUserInput())
        );
        Map<String, Object> analysis = new LinkedHashMap<>();
        analysis.put("summary", normalize(result.content()));
        analysis.put("source", "analyst");

        state.setCurrentTask("analysis-complete");
        state.setAnalysisResult(analysis);
        state.setNextAgent("supervisor");
        if (result.fallbackUsed()) {
            state.addMessage("Analyst 模型调用异常，已使用兜底分析结果。");
            state.addEvent("analyst", "supervisor", "模型异常，使用兜底分析并回传主管");
            log.info("Analyst completed analysis with fallback for current workflow, reason={}", result.reason());
        } else {
            state.addMessage("Analyst 已完成需求分析。");
            state.addEvent("analyst", "supervisor", "产出分析结果并回传给主管");
            log.info("Analyst completed analysis for current workflow");
        }
        return state;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim();
    }

    private String buildFallbackAnalysis(String userInput) {
        String requirement = normalize(userInput);
        return """
                1. 核心功能：围绕主需求实现最小可用闭环，优先完成关键路径功能与基础交互。
                2. 技术约束：保持与现有前后端技术栈一致，复用现有 API 规范与工程结构。
                3. 风险点：需求细节不完整、边界条件未定义、联调阶段可能出现字段对齐问题。
                4. 建议：先确认核心场景与验收标准，再按模块拆分迭代交付。

                用户需求原文：%s
                """.formatted(requirement);
    }
}
