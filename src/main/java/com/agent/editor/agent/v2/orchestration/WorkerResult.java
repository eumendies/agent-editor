package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.state.TaskStatus;

public record WorkerResult(
        String workerId,
        TaskStatus status,
        String summary,
        String updatedContent
) {
}
