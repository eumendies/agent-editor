package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentType;

import java.util.Map;

public class RoutingTaskOrchestrator implements TaskOrchestrator {

    private final Map<AgentType, TaskOrchestrator> delegates;

    public RoutingTaskOrchestrator(Map<AgentType, TaskOrchestrator> delegates) {
        this.delegates = delegates;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        TaskOrchestrator delegate = delegates.get(request.agentType());
        if (delegate == null) {
            throw new IllegalArgumentException("No orchestrator configured for " + request.agentType());
        }
        return delegate.execute(request);
    }
}
