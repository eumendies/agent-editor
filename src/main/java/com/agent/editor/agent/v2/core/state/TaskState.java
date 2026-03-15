package com.agent.editor.agent.v2.core.state;

public record TaskState(String taskId, TaskStatus status, String finalContent) {
}
