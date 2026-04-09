package com.agent.editor.agent.core.runtime;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.SupervisorAgent;
import com.agent.editor.agent.core.agent.SupervisorDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.context.SupervisorContext;
import com.agent.editor.agent.core.exception.InCorrectAgentException;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;

import java.util.List;

public class SupervisorExecutionRuntime implements ExecutionRuntime {

    private final EventPublisher eventPublisher;

    public SupervisorExecutionRuntime(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 使用空 worker 结果和空候选集创建默认 supervisor 初始上下文后执行。
     *
     * @param agent supervisor agent
     * @param request 本轮 supervisor 路由请求
     * @return supervisor 的单次路由决策结果
     */
    @Override
    public ExecutionResult<SupervisorDecision> run(Agent agent, ExecutionRequest request) {
        if (!(agent instanceof SupervisorAgent supervisorAgent)) {
            throw new InCorrectAgentException("SupervisorExecutionRuntime require SupervisorAgent type");
        }
        SupervisorContext initialContext = SupervisorContext.builder()
                .request(request)
                .iteration(0)
                .currentContent(request.getDocument().getContent())
                .memory(new com.agent.editor.agent.core.memory.ChatTranscriptMemory(List.of()))
                .stage(com.agent.editor.agent.core.state.ExecutionStage.RUNNING)
                .pendingReason(null)
                .toolSpecifications(List.of())
                .availableWorkers(List.of())
                .workerResults(List.of())
                .build();
        return runInternal(supervisorAgent, request, initialContext);
    }

    /**
     * 在显式提供的 {@link SupervisorContext} 上执行一次 supervisor 决策。
     *
     * @param agent supervisor agent
     * @param request 本轮 supervisor 路由请求
     * @param initialContext 调用方组装好的 supervisor 上下文
     * @return supervisor 的单次路由决策结果
     */
    @Override
    public ExecutionResult<SupervisorDecision> run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        if (!(agent instanceof SupervisorAgent supervisorAgent)) {
            throw new InCorrectAgentException("SupervisorExecutionRuntime require SupervisorAgent type");
        }
        if (!(initialContext instanceof SupervisorContext supervisorContext)) {
            throw new IllegalArgumentException("SupervisorExecutionRuntime require SupervisorContext initial state");
        }
        return runInternal(supervisorAgent, request, supervisorContext);
    }

    /**
     * 将当前 supervisor 上下文复制成独立快照，执行一次决策，并把决策摘要落入最终记忆。
     *
     * @param agent supervisor 实现
     * @param request 本轮 supervisor 路由请求
     * @param initialContext 作为决策输入的 supervisor 状态
     * @return 带有路由结果、最终消息和最终内容的执行结果
     */
    private ExecutionResult<SupervisorDecision> runInternal(SupervisorAgent agent,
                                                            ExecutionRequest request,
                                                            SupervisorContext initialContext) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.getTaskId(), "supervisor execution started"));

        SupervisorContext state = SupervisorContext.builder()
                .request(request)
                .iteration(initialContext.getIteration())
                .currentContent(initialContext.getCurrentContent())
                .memory(initialContext.getMemory())
                .stage(initialContext.getStage())
                .pendingReason(initialContext.getPendingReason())
                .toolSpecifications(initialContext.getToolSpecifications())
                .availableWorkers(initialContext.getAvailableWorkers())
                .workerResults(initialContext.getWorkerResults())
                .build();
        SupervisorDecision decision = agent.decide(state);
        String finalMessage = buildFinalMessage(decision);
        String finalContent = extractFinalContent(decision, state);

        eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.getTaskId(), finalMessage));

        AgentRunContext finalState = state
                .appendMemory(new ChatMessage.AiChatMessage(finalMessage))
                .withCurrentContent(finalContent)
                .markCompleted();
        return new ExecutionResult<>(decision, finalMessage, finalContent, finalState);
    }

    private String buildFinalMessage(SupervisorDecision decision) {
        if (decision instanceof SupervisorDecision.AssignWorker assignWorker) {
            return "assign worker: " + assignWorker.getWorkerId();
        }
        if (decision instanceof SupervisorDecision.Complete complete) {
            return "complete: " + complete.getSummary();
        }
        throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
    }

    private String extractFinalContent(SupervisorDecision decision, SupervisorContext state) {
        if (decision instanceof SupervisorDecision.AssignWorker) {
            return state.getCurrentContent();
        }
        if (decision instanceof SupervisorDecision.Complete complete) {
            return complete.getFinalContent();
        }
        throw new IllegalStateException("Unsupported decision type: " + decision.getClass().getSimpleName());
    }
}
