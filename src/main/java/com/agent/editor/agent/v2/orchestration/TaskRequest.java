package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;

public record TaskRequest(
        String taskId,
        String sessionId,
        AgentType agentType,
        DocumentSnapshot document,
        String instruction,
        int maxIterations
) {
}
