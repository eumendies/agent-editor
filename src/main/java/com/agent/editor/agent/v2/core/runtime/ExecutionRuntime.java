package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.state.ExecutionState;

public interface ExecutionRuntime {
    ExecutionResult run(AgentDefinition definition, ExecutionRequest request);

    default ExecutionResult run(AgentDefinition definition, ExecutionRequest request, ExecutionState initialState) {
        return run(definition, request);
    }
}
