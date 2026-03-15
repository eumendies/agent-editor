package com.agent.editor.agent.v2.supervisor;

import com.agent.editor.agent.v2.core.state.TaskStatus;

public record WorkerResult(
        String workerId,
        TaskStatus status,
        String summary,
        String updatedContent
) {
}
