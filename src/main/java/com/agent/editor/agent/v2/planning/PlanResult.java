package com.agent.editor.agent.v2.planning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanResult {

    private List<PlanStep> steps;
}
