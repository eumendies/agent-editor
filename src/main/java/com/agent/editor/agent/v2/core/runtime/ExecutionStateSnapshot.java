package com.agent.editor.agent.v2.core.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStateSnapshot {

    private int iteration;
    private int maxIterations;
}
