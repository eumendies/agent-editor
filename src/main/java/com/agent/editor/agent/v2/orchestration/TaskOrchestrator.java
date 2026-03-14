package com.agent.editor.agent.v2.orchestration;

public interface TaskOrchestrator {
    TaskResult execute(TaskRequest request);
}
