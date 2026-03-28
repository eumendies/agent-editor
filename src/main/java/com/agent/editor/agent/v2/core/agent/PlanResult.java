package com.agent.editor.agent.v2.core.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.IntStream;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlanResult {
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
        private int order;
        private String instruction;
    }
}
