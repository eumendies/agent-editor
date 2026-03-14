package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;

public interface ExecutionRuntime {
    ExecutionResult run(AgentDefinition definition, ExecutionRequest request);
}
