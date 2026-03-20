package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;

import java.util.List;

public record TaskRequest(
        String taskId,
        String sessionId,
        AgentType agentType,
        DocumentSnapshot document,
        String instruction,
        int maxIterations,
        ExecutionMemory memory
) {
    public TaskRequest(String taskId,
                       String sessionId,
                       AgentType agentType,
                       DocumentSnapshot document,
                       String instruction,
                       int maxIterations) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, new ChatTranscriptMemory(List.of()));
    }
}
