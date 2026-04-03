package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.IntStream;

/**
 * planning agent 输出的结构化计划结果，按顺序保存待执行步骤。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanResult {
    // 规划阶段生成的步骤列表。
    private List<PlanStep> plans;

    public PlanResult withInstructions(List<String> instructions) {
        List<PlanStep> steps = IntStream.range(1, instructions.size() + 1)
                .mapToObj(i -> new PlanStep(i, instructions.get(i - 1)))
                .toList();
        this.setPlans(steps);
        return this;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public class PlanStep {
        // 步骤顺序，从 1 开始。
        private int order;
        // 当前步骤的执行指令。
        private String instruction;
    }
}
