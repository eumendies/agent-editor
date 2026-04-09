package com.agent.editor.agent.task;

import com.agent.editor.agent.core.agent.AgentType;

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
        TaskOrchestrator delegate = delegates.get(request.getAgentType());
        if (delegate == null) {
            throw new IllegalArgumentException("No orchestrator configured for " + request.getAgentType());
        }
        return delegate.execute(request);
    }
}
