package com.agent.editor.agent.v2.planning;

import java.util.List;

public record PlanningResponse(List<Step> steps) {

    public PlanningResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record Step(String instruction) {
    }
}
