package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;

public interface ExecutionRuntime {
    ExecutionResult run(Agent agent, ExecutionRequest request);

    default ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        return run(agent, request);
    }
}
