package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;

public record ExecutionRequest(
        String taskId,
        String sessionId,
        AgentType agentType,
        DocumentSnapshot document,
        String instruction,
        int maxIterations
) {
}
