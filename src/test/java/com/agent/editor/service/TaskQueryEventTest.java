package com.agent.editor.service;

import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskQueryEventTest {

    @Test
    void shouldStoreEventsAndExposeLegacySteps() {
        TaskQueryService service = new TaskQueryService();
        service.appendEvent(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));
        service.appendEvent(new ExecutionEvent(EventType.TASK_COMPLETED, "task-1", "done"));

        assertEquals(2, service.getEvents("task-1").size());
        assertEquals(EventType.TOOL_CALLED, service.getEvents("task-1").get(0).getType());
        assertEquals("editDocument", service.getEvents("task-1").get(0).getMessage());
        assertEquals(2, service.getTaskSteps("task-1").size());
        assertEquals("editDocument", service.getTaskSteps("task-1").get(0).getAction());
        assertEquals("done", service.getTaskSteps("task-1").get(1).getResult());
    }

    @Test
    void shouldStoreTextStreamingEventsInOrder() {
        TaskQueryService service = new TaskQueryService();
        service.appendEvent(new ExecutionEvent(EventType.TEXT_STREAM_STARTED, "task-stream", ""));
        service.appendEvent(new ExecutionEvent(EventType.TEXT_STREAM_DELTA, "task-stream", "hello "));
        service.appendEvent(new ExecutionEvent(EventType.TEXT_STREAM_DELTA, "task-stream", "world"));
        service.appendEvent(new ExecutionEvent(EventType.TEXT_STREAM_COMPLETED, "task-stream", ""));

        assertEquals(4, service.getEvents("task-stream").size());
        assertEquals(EventType.TEXT_STREAM_STARTED, service.getEvents("task-stream").get(0).getType());
        assertEquals("hello ", service.getEvents("task-stream").get(1).getMessage());
        assertEquals("world", service.getEvents("task-stream").get(2).getMessage());
        assertEquals(EventType.TEXT_STREAM_COMPLETED, service.getEvents("task-stream").get(3).getType());
    }
}
