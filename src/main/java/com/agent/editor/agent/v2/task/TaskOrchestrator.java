package com.agent.editor.agent.v2.task;

public interface TaskOrchestrator {
    TaskResult execute(TaskRequest request);
}
