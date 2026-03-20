package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;

public class ReActAgentOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime runtime;
    private final AgentDefinition agentDefinition;

    public ReActAgentOrchestrator(ExecutionRuntime runtime, AgentDefinition agentDefinition) {
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
