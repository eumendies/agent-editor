package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.core.agent.AgentType;

import java.util.Map;

/**
 * 按 agent type 把任务分流到不同编排链，避免在 application service 里堆 if/else。
 */
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
