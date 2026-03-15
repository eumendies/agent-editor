package com.agent.editor.agent.v2.orchestration;

import com.agent.editor.agent.v2.core.state.TaskStatus;

public record TaskResult(TaskStatus status, String finalContent) {
}
