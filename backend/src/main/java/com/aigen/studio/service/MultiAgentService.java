package com.aigen.studio.service;

import com.aigen.studio.agent.AgentEvent;
import com.aigen.studio.agent.AgentOrchestrator;
import com.aigen.studio.agent.AgentState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiAgentService {

    private final AgentOrchestrator agentOrchestrator;

    @Qualifier("taskExecutor")
    private final Executor taskExecutor;

    public String understandRequirement(String requirement, Long conversationId, Consumer<String> progressConsumer) {
        AgentState state = agentOrchestrator.execute(requirement);
        emitTrace(state, progressConsumer);
        String result = buildUnderstandingOutput(state);
        emitProgress(progressConsumer, result);
        log.info("Multi-agent workflow finished for conversation={}, iterations={}",
                conversationId, state.getIterations());
        return result;
    }

    public CompletableFuture<AgentState> executeAsync(String requirement) {
        return CompletableFuture.supplyAsync(() -> agentOrchestrator.execute(requirement), taskExecutor);
    }

    public String renderFlow(AgentState state) {
        String flow = agentOrchestrator.renderFlow(state);
        if (flow != null && !flow.isBlank()) {
            return flow;
        }
        return buildFallbackFlow(state);
    }

    private void emitTrace(AgentState state, Consumer<String> progressConsumer) {
        if (progressConsumer == null || state == null || state.getEvents() == null) {
            return;
        }
        for (AgentEvent event : state.getEvents()) {
            progressConsumer.accept("[%s] -> [%s] %s".formatted(
                    safe(event.getAgent()),
                    safe(event.getNextAgent()),
                    safe(event.getDetail())
            ));
        }
    }

    private String buildUnderstandingOutput(AgentState state) {
        String analysis = resolveAnalysisSummary(state);
        String uiDesign = safe(state.getUiDesign());
        String implementation = safe(state.getGeneratedCode());

        return """
                ## 多智能体理解结果

                ### 需求分析
                %s

                ### UI 设计建议
                %s

                ### 实现方案
                %s
                """.formatted(analysis, uiDesign, implementation);
    }

    private String resolveAnalysisSummary(AgentState state) {
        if (state.getAnalysisResult() == null || state.getAnalysisResult().isEmpty()) {
            return "";
        }
        Object summary = state.getAnalysisResult().get("summary");
        if (summary instanceof String text && !text.isBlank()) {
            return text;
        }
        return state.getAnalysisResult().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private void emitProgress(Consumer<String> progressConsumer, String output) {
        if (progressConsumer != null && output != null && !output.isBlank()) {
            progressConsumer.accept(output);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildFallbackFlow(AgentState state) {
        StringBuilder builder = new StringBuilder("graph TD\n");
        if (state == null || state.getEvents() == null || state.getEvents().isEmpty()) {
            builder.append("    Start --> End");
            return builder.toString();
        }

        for (AgentEvent event : state.getEvents()) {
            builder.append("    ")
                    .append(safe(event.getAgent()))
                    .append(" --> ")
                    .append(safe(event.getNextAgent()))
                    .append('\n');
        }
        return builder.toString();
    }
}
