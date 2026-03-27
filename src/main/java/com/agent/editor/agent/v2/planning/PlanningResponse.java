package com.agent.editor.agent.v2.planning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class PlanningResponse {

    private List<Step> steps = List.of();

    public PlanningResponse(List<Step> steps) {
        setSteps(steps);
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps == null ? List.of() : List.copyOf(steps);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {

        private String instruction;
    }
}
