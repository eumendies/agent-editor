package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.memory.ExecutionMemory;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class TaskRequest {

    private String taskId;
    private String sessionId;
    private AgentType agentType;
    private DocumentSnapshot document;
    private String instruction;
    private int maxIterations;
    private ExecutionMemory memory;
    private String userProfileGuidance = "";

    public TaskRequest(String taskId,
                       String sessionId,
                       AgentType agentType,
                       DocumentSnapshot document,
                       String instruction,
                       int maxIterations,
                       ExecutionMemory memory,
                       String userProfileGuidance) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.document = document;
        this.instruction = instruction;
        this.maxIterations = maxIterations;
        this.memory = memory;
        setUserProfileGuidance(userProfileGuidance);
    }

    public TaskRequest(String taskId,
                       String sessionId,
                       AgentType agentType,
                       DocumentSnapshot document,
                       String instruction,
                       int maxIterations,
                       ExecutionMemory memory) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, memory, "");
    }

    public TaskRequest(String taskId,
                       String sessionId,
                       AgentType agentType,
                       DocumentSnapshot document,
                       String instruction,
                       int maxIterations) {
        this(taskId, sessionId, agentType, document, instruction, maxIterations, new ChatTranscriptMemory(List.of()));
    }

    public void setUserProfileGuidance(String userProfileGuidance) {
        this.userProfileGuidance = userProfileGuidance == null ? "" : userProfileGuidance;
    }
}
