package com.agent.editor.agent.task;

/**
 * 任务级编排入口。
 * 它负责选择并驱动一整条工作流，而不是单次模型决策。
 */
public interface TaskOrchestrator {
    /**
     * 执行一次完整的任务级工作流。
     *
     * @param request 任务输入，包括文档、用户指令和运行预算
     * @return 任务最终状态、最终文档内容以及需要保留的记忆
     */
    TaskResult execute(TaskRequest request);
}
