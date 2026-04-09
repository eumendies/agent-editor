package com.agent.editor.agent.core.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行时迭代进度的轻量快照，用于上层观测执行推进情况。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStateSnapshot {

    // 当前已执行到的迭代轮次。
    private int iteration;
    // 允许执行的最大轮次。
    private int maxIterations;
}
