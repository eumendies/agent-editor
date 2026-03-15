package com.agent.editor.agent.v2.definition;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.runtime.ExecutionContext;

public interface AgentDefinition {
    AgentType type();

    Decision decide(ExecutionContext context);
}
