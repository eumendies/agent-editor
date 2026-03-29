package com.agent.editor.agent.v2.planning;

import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.PlanningAgent;
import com.agent.editor.agent.v2.core.context.AgentRunContext;
import com.agent.editor.agent.v2.core.agent.PlanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Planning agent 只负责把原始任务拆成结构化步骤，不直接执行工具。
 */
public class PlanningAgentImpl implements PlanningAgent {

    private final PlanningAiService planningAiService;

    public PlanningAgentImpl(PlanningAiService planningAiService) {
        this.planningAiService = planningAiService;
    }

    @Override
    public AgentType type() {
        return AgentType.PLANNING;
    }

    @Override
    public PlanResult createPlan(AgentRunContext context) {
        String instruction = context.getRequest().getInstruction();
        String documentContent = context.getCurrentContent();

        // 没有模型时退化成单步计划，保证 orchestration 仍然可跑通。
        if (planningAiService == null) {
            return fallbackPlan(context.getRequest().getInstruction());
        }

        try {
            PlanningResponse response = planningAiService.plan(documentContent, instruction);
            return toPlanResult(response, instruction);
        } catch (RuntimeException exception) {
            return fallbackPlan(instruction);
        }
    }

    private PlanResult toPlanResult(PlanningResponse response, String instruction) {
        if (response == null || response.getSteps() == null || response.getSteps().isEmpty()) {
            return fallbackPlan(instruction);
        }

        List<String> instructionList = new ArrayList<>();
        for (PlanningResponse.Step step : response.getSteps()) {
            if (step.getInstruction() == null || step.getInstruction().isEmpty()) {
                continue;
            }
            instructionList.add(step.getInstruction());
        }

        PlanResult planResult = new PlanResult();
        return planResult.withInstructions(instructionList);
    }

    private PlanResult fallbackPlan(String instruction) {
        PlanResult result = new PlanResult();
        return result.withInstructions(List.of(instruction));
    }
}
