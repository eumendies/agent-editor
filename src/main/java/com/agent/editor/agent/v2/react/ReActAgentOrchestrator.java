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

public class ReActAgentOrchestrator implements TaskOrchestrator {

    private final ExecutionRuntime runtime;
    private final Agent agent;
    private final ReactAgentContextFactory contextFactory;

    public ReActAgentOrchestrator(ExecutionRuntime runtime, Agent agent, ReactAgentContextFactory contextFactory) {
        this.runtime = runtime;
        this.agent = agent;
        this.contextFactory = contextFactory;
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
        ExecutionResult result = runtime.run(
                agent,
                new ExecutionRequest(
                        request.getTaskId(),
                        request.getSessionId(),
                        request.getAgentType(),
                        request.getDocument(),
                        request.getInstruction(),
                        request.getMaxIterations()
                ),
                initialState
        );
        return new TaskResult(TaskStatus.COMPLETED, result.getFinalContent(), result.getFinalState().getMemory());
    }
}
