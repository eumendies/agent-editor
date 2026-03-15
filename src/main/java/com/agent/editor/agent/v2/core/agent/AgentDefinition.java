package com.agent.editor.agent.v2.core.agent;

import com.agent.editor.agent.v2.core.runtime.ExecutionContext;

public interface AgentDefinition {
    AgentType type();

    Decision decide(ExecutionContext context);
}
