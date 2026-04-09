package com.agent.editor.agent.core.state;

/**
 * 任务面向外部编排层暴露的生命周期状态。
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    PARTIAL
}
