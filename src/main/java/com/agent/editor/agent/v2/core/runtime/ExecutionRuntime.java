package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;

public interface ExecutionRuntime {
    ExecutionResult run(AgentDefinition definition, ExecutionRequest request);
}
