package com.agent.editor.agent.v2.task;

import com.agent.editor.agent.v2.core.state.TaskStatus;

public record TaskResult(TaskStatus status, String finalContent) {
}
