package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;

import java.util.List;

public record WorkerDefinition(
        String workerId,
        String role,
        String description,
        AgentDefinition agentDefinition,
        List<String> allowedTools,
        List<String> capabilities
) {
    public WorkerDefinition(
            String workerId,
            String role,
            String description,
            AgentDefinition agentDefinition,
            List<String> allowedTools
    ) {
        this(workerId, role, description, agentDefinition, allowedTools, List.of());
    }

    public WorkerDefinition {
        allowedTools = List.copyOf(allowedTools);
        // capability 标签会被 supervisor 用来做候选筛选，因此这里固定成不可变快照。
        capabilities = List.copyOf(capabilities);
    }
}
