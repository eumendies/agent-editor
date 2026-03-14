package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.state.DocumentSnapshot;

import java.util.List;

public record ExecutionRequest(
        String taskId,
        String sessionId,
        AgentType agentType,
        DocumentSnapshot document,
        String instruction,
        int maxIterations,
        String workerId,
        List<String> allowedTools
) {
    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations,
                            List<String> allowedTools) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, null, allowedTools);
    }

    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, null, List.of());
    }

    public ExecutionRequest {
        allowedTools = List.copyOf(allowedTools);
    }
}
