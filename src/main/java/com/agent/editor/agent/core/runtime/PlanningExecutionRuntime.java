package com.agent.editor.agent.core.runtime;

import com.agent.editor.agent.core.agent.Agent;
import com.agent.editor.agent.core.agent.PlanResult;
import com.agent.editor.agent.core.agent.PlanningAgent;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.exception.InCorrectAgentException;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.event.EventPublisher;
import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;

import java.util.stream.Collectors;

public class PlanningExecutionRuntime implements ExecutionRuntime {

    private final EventPublisher eventPublisher;

    public PlanningExecutionRuntime(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 使用默认初始上下文执行 planner。
     *
     * @param agent planner agent
     * @param request 本次规划请求
     * @return 仅包含计划结果与计划摘要的执行结果
     */
    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request) {
        if (!(agent instanceof PlanningAgent)) {
            throw new InCorrectAgentException("PlanningExecutionRuntime require PlanningAgent type");
        }
        return runInternal((PlanningAgent) agent, request, new AgentRunContext(0, request.getDocument().getContent()).withRequest(request));
    }

    /**
     * 在调用方提供的 planning 上下文中执行 planner。
     *
     * @param agent planner agent
     * @param request 本次规划请求
     * @param initialContext 预先构造的 planning 上下文
     * @return 仅包含计划结果与计划摘要的执行结果
     */
    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        if (!(agent instanceof PlanningAgent)) {
            throw new InCorrectAgentException("PlanningExecutionRuntime require PlanningAgent type");
        }
        return runInternal((PlanningAgent) agent, request, initialContext);
    }

    /**
     * 执行一次单轮 planning，并把计划摘要写回最终状态记忆。
     *
     * @param agent planner 实现
     * @param request 本次规划请求
     * @param initialContext planner 起始上下文
     * @return 带有结构化计划和摘要文本的执行结果
     */
    public ExecutionResult runInternal(PlanningAgent agent, ExecutionRequest request, AgentRunContext initialContext) {
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_STARTED, request.getTaskId(), "execution started"));

        AgentRunContext state = initialContext
                .withRequest(request);
        PlanResult planResult = agent.createPlan(state);
        String planSummary = buildPlanSummary(planResult);

        eventPublisher.publish(new ExecutionEvent(EventType.PLAN_CREATED, request.getTaskId(), planSummary));
        eventPublisher.publish(new ExecutionEvent(EventType.TASK_COMPLETED, request.getTaskId(), planSummary));

        // planning runtime 只产出计划和对话记忆，不直接修改文档内容。
        AgentRunContext completedState = state
                .appendMemory(new ChatMessage.AiChatMessage(planSummary))
                .markCompleted();
        return new ExecutionResult<>(planResult, planSummary, state.getCurrentContent(), completedState);
    }

    private String buildPlanSummary(PlanResult planResult) {
        int stepCount = planResult == null || planResult.getPlans() == null ? 0 : planResult.getPlans().size();
        if (stepCount == 0) {
            return "plan created with 0 step(s)";
        }
        String instructions = planResult.getPlans().stream()
                .map(PlanResult.PlanStep::getInstruction)
                .collect(Collectors.joining(" | "));
        return "plan created with %d step(s): %s".formatted(stepCount, instructions);
    }
}
