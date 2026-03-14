package com.agent.editor.agent.v2.event;

public record ExecutionEvent(EventType type, String taskId, String message) {
}
