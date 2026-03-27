package com.agent.editor.agent.v2.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionEventTest {

    @Test
    void shouldCreateEventWithTypeAndTaskId() {
        ExecutionEvent event = new ExecutionEvent(EventType.TASK_STARTED, "task-1", "started");

        assertEquals(EventType.TASK_STARTED, event.getType());
        assertEquals("task-1", event.getTaskId());
        assertEquals("started", event.getMessage());
    }
}
