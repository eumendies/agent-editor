package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequest {

    private String taskId;
    private String sessionId;
    private AgentType agentType;
    private DocumentSnapshot document;
    private String instruction;
    private int maxIterations;
    private ExecutionMemory memory;

    public TaskRequest(String taskId,
                       String sessionId,
                       AgentType agentType,
                       DocumentSnapshot document,
                       String instruction,
                       int maxIterations) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, new ChatTranscriptMemory(List.of()));
    }
}
