package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ExecutionRequest {

    private String taskId;
    private String sessionId;
    private AgentType agentType;
    private DocumentSnapshot document;
    private String instruction;
    private int maxIterations;
    private String workerId;
    private List<String> allowedTools = List.of();

    public ExecutionRequest(String taskId,
                            String sessionId,
                            AgentType agentType,
                            DocumentSnapshot document,
                            String instruction,
                            int maxIterations,
                            String workerId,
                            List<String> allowedTools) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.agentType = agentType;
        this.document = document;
        this.instruction = instruction;
        this.maxIterations = maxIterations;
        this.workerId = workerId;
        setAllowedTools(allowedTools);
    }

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

    public void setAllowedTools(List<String> allowedTools) {
        this.allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
    }
}
