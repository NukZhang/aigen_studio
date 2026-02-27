package com.aigen.studio.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {

    private String userInput;
    private String currentTask;

    @Builder.Default
    private Map<String, Object> analysisResult = new LinkedHashMap<>();

    @Builder.Default
    private String uiDesign = "";

    @Builder.Default
    private String generatedCode = "";

    @Builder.Default
    private List<String> messages = new ArrayList<>();

    @Builder.Default
    private String nextAgent = "supervisor";

    @Builder.Default
    private int iterations = 0;

    @Builder.Default
    private List<AgentEvent> events = new ArrayList<>();

    public static AgentState initial(String userInput) {
        return AgentState.builder()
                .userInput(userInput == null ? "" : userInput.trim())
                .nextAgent("supervisor")
                .build();
    }

    public void addMessage(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        messages.add(message.trim());
    }

    public void addEvent(String agent, String nextAgent, String detail) {
        events.add(AgentEvent.builder()
                .timestamp(LocalDateTime.now())
                .agent(agent == null ? "" : agent.trim())
                .nextAgent(nextAgent == null ? "" : nextAgent.trim())
                .detail(detail == null ? "" : detail.trim())
                .build());
    }
}
