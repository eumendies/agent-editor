package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.Agent;
import com.agent.editor.agent.v2.core.agent.PlanResult;
import com.agent.editor.agent.v2.core.agent.PlanningAgent;
import com.agent.editor.agent.v2.core.exception.InCorrectAgentException;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;

import java.util.stream.Collectors;

public class PlanningExecutionRuntime implements ExecutionRuntime {

    private final EventPublisher eventPublisher;

    public PlanningExecutionRuntime(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request) {
        if (!(agent instanceof PlanningAgent)) {
            throw new InCorrectAgentException("PlanningExecutionRuntime require PlanningAgent type");
        }
        return runInternal((PlanningAgent) agent, request, new AgentRunContext(0, request.getDocument().getContent()).withRequest(request));
    }

    @Override
    public ExecutionResult run(Agent agent, ExecutionRequest request, AgentRunContext initialContext) {
        if (!(agent instanceof PlanningAgent)) {
            throw new InCorrectAgentException("PlanningExecutionRuntime require PlanningAgent type");
        }
        return runInternal((PlanningAgent) agent, request, initialContext);
    }

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
