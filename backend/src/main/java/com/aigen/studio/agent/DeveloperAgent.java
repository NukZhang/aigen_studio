package com.aigen.studio.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeveloperAgent {

    private final ChatLanguageModel chatLanguageModel;

    public AgentState execute(AgentState state) {
        String analysisSummary = state.getAnalysisResult() == null
                ? ""
                : String.valueOf(state.getAnalysisResult().getOrDefault("summary", ""));
        String prompt = """
                你是开发智能体，请基于需求分析和 UI 方案生成实现计划：
                - 后端模块
                - 前端模块
                - 关键接口
                - 交付步骤

                用户需求：
                %s

                分析摘要：
                %s

                UI 方案：
                %s
                """.formatted(state.getUserInput(), analysisSummary, state.getUiDesign());

        AgentModelRunner.ModelExecutionResult result = AgentModelRunner.generateWithHeartbeat(
                "Developer",
                chatLanguageModel,
                prompt,
                () -> buildFallbackImplementationPlan(state)
        );
        state.setCurrentTask("implementation-plan-complete");
        state.setGeneratedCode(result.content());
        state.setNextAgent("supervisor");
        if (result.fallbackUsed()) {
            if (result.timeout()) {
                state.addMessage("Developer 模型调用超时，已使用兜底实现方案。");
                state.addEvent("developer", "supervisor", "模型调用超时，已输出兜底实现方案");
            } else {
                state.addMessage("Developer 模型调用异常，已使用兜底实现方案。");
                state.addEvent("developer", "supervisor", "模型调用异常，已输出兜底实现方案");
            }
            log.info("Developer completed implementation planning with fallback for current workflow, reason={}", result.reason());
        } else {
            state.addMessage("Developer 已输出实现方案。");
            state.addEvent("developer", "supervisor", "实现方案已完成并回传主管");
            log.info("Developer completed implementation planning for current workflow");
        }
        return state;
    }

    private String buildFallbackImplementationPlan(AgentState state) {
        String requirement = safe(state.getUserInput());
        String summary = state.getAnalysisResult() == null
                ? ""
                : safe(String.valueOf(state.getAnalysisResult().getOrDefault("summary", "")));
        String uiDesign = safe(state.getUiDesign());

        return """
                ### 后端模块
                - 按需求拆分核心业务服务与控制器，优先落地最小可运行主流程。
                - 定义基础数据模型与持久化接口，保证关键字段可追踪与可审计。
                - 补齐关键异常处理与日志，确保后续联调可快速定位问题。

                ### 前端模块
                - 基于现有 UI 方案搭建页面骨架，先完成主流程页面和核心交互。
                - 封装与后端接口对应的 API 层，统一处理加载态、错误态与空态。
                - 对关键表单与列表增加基础校验与反馈，保证可用性闭环。

                ### 关键接口
                - 围绕“创建-查询-更新”主链路定义 REST 接口与字段契约。
                - 明确分页、筛选、状态流转等参数，保持前后端对齐。
                - 为高频接口增加返回示例与错误码约定，降低联调成本。

                ### 交付步骤
                1. 先完成数据模型与接口草案并与前端字段对齐。
                2. 并行推进后端主流程实现与前端页面集成。
                3. 执行联调与回归测试，修复关键缺陷后输出交付物。

                ### 上下文摘要
                - 用户需求：%s
                - 分析摘要：%s
                - UI 方案：%s
                """.formatted(requirement, summary, uiDesign);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
