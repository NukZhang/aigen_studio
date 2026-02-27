package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DesignerAgent {

    private final ChatLanguageModel chatLanguageModel;

    public AgentState execute(AgentState state) {
        String analysisSummary = state.getAnalysisResult() == null
                ? ""
                : String.valueOf(state.getAnalysisResult().getOrDefault("summary", ""));
        String prompt = """
                你是 UI 设计智能体，请基于需求分析给出页面和交互设计建议，输出简洁可执行方案。

                用户需求：
                %s

                需求分析摘要：
                %s
                """.formatted(state.getUserInput(), analysisSummary);

        AgentModelRunner.ModelExecutionResult result = AgentModelRunner.generateWithHeartbeat(
                "Designer",
                chatLanguageModel,
                prompt,
                () -> buildFallbackUiDesign(state.getUserInput(), analysisSummary)
        );
        state.setCurrentTask("ui-design-complete");
        state.setUiDesign(result.content());
        state.setNextAgent("supervisor");
        if (result.fallbackUsed()) {
            state.addMessage("Designer 模型调用异常，已使用兜底 UI 方案。");
            state.addEvent("designer", "supervisor", "模型异常，使用兜底 UI 方案并回传主管");
            log.info("Designer completed UI design with fallback for current workflow, reason={}", result.reason());
        } else {
            state.addMessage("Designer 已输出 UI 设计方案。");
            state.addEvent("designer", "supervisor", "设计完成，回传主管进行下一步分派");
            log.info("Designer completed UI design for current workflow");
        }
        return state;
    }

    private String buildFallbackUiDesign(String requirement, String analysisSummary) {
        String normalizedRequirement = requirement == null ? "" : requirement.trim();
        String normalizedSummary = analysisSummary == null ? "" : analysisSummary.trim();
        return """
                ### 页面结构建议
                - 首页：展示核心入口、主要信息卡片与关键操作按钮。
                - 列表/内容区：承载业务主内容，支持筛选、排序与状态反馈。
                - 结果/详情区：展示核心结果信息，提供复制、分享、返回等操作。

                ### 交互建议
                - 关键动作采用主按钮突出，次级操作使用轻量入口。
                - 对加载、空数据、异常场景提供明确提示与恢复路径。
                - 统一使用响应式布局，保证移动端优先体验。

                ### 上下文摘要
                - 用户需求：%s
                - 分析摘要：%s
                """.formatted(normalizedRequirement, normalizedSummary);
    }
}
