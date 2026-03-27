package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.runtime.AgentRunContext;
import com.agent.editor.agent.v2.core.state.DocumentSnapshot;

import java.util.List;

/**
 * Planning agent 只负责把原始任务拆成结构化步骤，不直接执行工具。
 */
public class PlanningAgentDefinition implements AgentDefinition {

    private final PlanningAiService planningAiService;

    public PlanningAgentDefinition(PlanningAiService planningAiService) {
        this.planningAiService = planningAiService;
    }

    @Override
    public AgentType type() {
        return AgentType.PLANNING;
    }

    @Override
    public Decision decide(AgentRunContext context) {
        // 兼容统一 runtime：当 planner 被当作普通 agent 运行时，返回可展示的计划文本。
        PlanResult plan = createPlan(context.getRequest().getDocument(), context.getRequest().getInstruction());
        String result = plan.getSteps().stream()
                .map(step -> step.getOrder() + ". " + step.getInstruction())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(context.getRequest().getInstruction());
        return new Decision.Complete(result, "planning complete");
    }

    public PlanResult createPlan(DocumentSnapshot document, String instruction) {
        // 没有模型时退化成单步计划，保证 orchestration 仍然可跑通。
        if (planningAiService == null) {
            return fallbackPlan(instruction);
        }

        try {
            PlanningResponse response = planningAiService.plan(document.getContent(), instruction);
            return toPlanResult(response, instruction);
        } catch (RuntimeException exception) {
            return fallbackPlan(instruction);
        }
    }

    private PlanResult toPlanResult(PlanningResponse response, String fallbackInstruction) {
        if (response == null || response.getSteps() == null || response.getSteps().isEmpty()) {
            return fallbackPlan(fallbackInstruction);
        }

        List<PlanStep> steps = new java.util.ArrayList<>();
        int nextOrder = 1;
        for (PlanningResponse.Step step : response.getSteps()) {
            if (step == null || step.getInstruction() == null || step.getInstruction().isBlank()) {
                continue;
            }
            steps.add(new PlanStep(nextOrder++, step.getInstruction().trim()));
        }

        if (steps.isEmpty()) {
            return fallbackPlan(fallbackInstruction);
        }
        return new PlanResult(steps);
    }

    private PlanResult fallbackPlan(String instruction) {
        return new PlanResult(List.of(new PlanStep(1, instruction)));
    }
}
