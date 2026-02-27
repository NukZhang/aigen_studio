package com.aigen.studio.agent;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AgentEvent {
    LocalDateTime timestamp;
    String agent;
    String nextAgent;
    String detail;
}
