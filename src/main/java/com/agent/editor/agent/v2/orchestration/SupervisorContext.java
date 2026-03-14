package com.agent.editor.agent.v2.orchestration;

import java.util.List;

public record SupervisorContext(
        String taskId,
        String sessionId,
        String originalInstruction,
        String currentContent,
        List<WorkerDefinition> availableWorkers,
        List<WorkerResult> workerResults
) {
}
