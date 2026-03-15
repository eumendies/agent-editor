package com.agent.editor.agent.v2.task;

/**
 * 任务级编排入口。
 * 它负责选择并驱动一整条工作流，而不是单次模型决策。
 */
public interface TaskOrchestrator {
    TaskResult execute(TaskRequest request);
}
