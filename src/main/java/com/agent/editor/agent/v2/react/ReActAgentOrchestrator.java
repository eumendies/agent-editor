package com.agent.editor.agent.v2.react;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.runtime.ExecutionRequest;
import com.agent.editor.agent.v2.core.runtime.ExecutionResult;
import com.agent.editor.agent.v2.core.runtime.ExecutionRuntime;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.agent.v2.task.TaskRequest;
import com.agent.editor.agent.v2.task.TaskResult;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessPolicy;
import com.agent.editor.agent.v2.tool.ExecutionToolAccessRole;
import com.agent.editor.agent.v2.tool.document.DocumentToolMode;

public class ReActAgentOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime runtime;
    private final Agent agent;
    private final ReactAgentContextFactory contextFactory;
    private final ExecutionToolAccessPolicy executionToolAccessPolicy;

    public ReActAgentOrchestrator(ExecutionRuntime runtime,
                                  Agent agent,
                                  ReactAgentContextFactory contextFactory,
                                  ExecutionToolAccessPolicy executionToolAccessPolicy) {
        this.runtime = runtime;
        this.agent = agent;
        this.contextFactory = contextFactory;
        this.executionToolAccessPolicy = executionToolAccessPolicy;
    }

    /**
     * 用单个 ReAct agent 执行整个任务。
     *
     * @param request 任务输入
     * @return runtime 最终状态映射出的任务结果
     */
    @Override
    public TaskResult execute(TaskRequest request) {
        AgentRunContext initialState = contextFactory.prepareInitialContext(request);
        DocumentToolMode documentToolMode = executionToolAccessPolicy.resolveMode(request.getDocument());
        ExecutionRequest executionRequest = new ExecutionRequest(
                request.getTaskId(),
                request.getSessionId(),
                request.getAgentType(),
                request.getDocument(),
                request.getInstruction(),
                request.getMaxIterations(),
                executionToolAccessPolicy.allowedTools(documentToolMode, ExecutionToolAccessRole.MAIN_WRITE)
        );
        executionRequest.setUserProfileGuidance(request.getUserProfileGuidance());
        executionRequest.setDocumentToolMode(documentToolMode);
        ExecutionResult result = runtime.run(
                agent,
                executionRequest,
                initialState
        );
        return new TaskResult(TaskStatus.COMPLETED, result.getFinalContent(), result.getFinalState().getMemory());
    }
}
