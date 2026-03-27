package com.agent.editor.agent.v2.planning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {

    private int order;
    private String instruction;
}
