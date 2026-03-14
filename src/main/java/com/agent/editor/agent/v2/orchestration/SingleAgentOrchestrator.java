package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.runtime.ExecutionResult;
import com.agent.editor.agent.v2.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.state.TaskStatus;

public class SingleAgentOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime runtime;
    private final AgentDefinition agentDefinition;

    public SingleAgentOrchestrator(ExecutionRuntime runtime, AgentDefinition agentDefinition) {
        this.runtime = runtime;
        this.agentDefinition = agentDefinition;
    }

    @Override
    public TaskResult execute(TaskRequest request) {
        ExecutionResult result = runtime.run(
                agentDefinition,
                new ExecutionRequest(
                        request.taskId(),
                        request.sessionId(),
                        request.agentType(),
                        request.document(),
                        request.instruction(),
                        request.maxIterations()
                )
        );
        return new TaskResult(TaskStatus.COMPLETED, result.finalContent());
    }
}
