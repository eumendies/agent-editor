package com.agent.editor.agent.v2.supervisor;

import java.util.List;

public record SupervisorContext(
        String taskId,
        String sessionId,
        String originalInstruction,
        String currentContent,
        List<WorkerDefinition> availableWorkers,
        List<WorkerResult> workerResults
) {
    public SupervisorContext {
        availableWorkers = List.copyOf(availableWorkers);
        workerResults = List.copyOf(workerResults);
    }
}
