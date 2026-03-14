package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.runtime.ExecutionContext;

public interface AgentDefinition {
    AgentType type();

    Decision decide(ExecutionContext context);
}
