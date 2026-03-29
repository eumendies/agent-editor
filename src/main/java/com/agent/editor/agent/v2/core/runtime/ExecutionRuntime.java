package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.context.AgentRunContext;

public interface ExecutionRuntime {
    ExecutionResult run(Agent agent, ExecutionRequest request);

    default ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        return run(agent, request);
    }
}
