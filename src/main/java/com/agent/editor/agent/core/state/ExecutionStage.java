package com.agent.editor.agent.core.state;

/**
 * 单次 agent 执行在运行时上下文中的阶段标记。
 */
public enum ExecutionStage {
    RUNNING,
    COMPLETED,
    WAITING_FOR_HUMAN,
    FAILED
}
