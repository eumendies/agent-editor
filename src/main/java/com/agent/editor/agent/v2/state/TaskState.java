package com.agent.editor.agent.v2.state;

public record TaskState(String taskId, TaskStatus status, String finalContent) {
}
