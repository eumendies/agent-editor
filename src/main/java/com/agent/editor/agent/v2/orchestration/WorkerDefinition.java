package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentDefinition;

import java.util.List;

public record WorkerDefinition(
        String workerId,
        String role,
        String description,
        AgentDefinition agentDefinition,
        List<String> allowedTools
) {
    public WorkerDefinition {
        allowedTools = List.copyOf(allowedTools);
    }
}
