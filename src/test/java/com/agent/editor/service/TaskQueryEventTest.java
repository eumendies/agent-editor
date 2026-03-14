package com.agent.editor.service;

import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskQueryEventTest {

    @Test
    void shouldStoreEventsAndExposeLegacySteps() {
        TaskQueryService service = new TaskQueryService();
        service.appendEvent(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));
        service.appendEvent(new ExecutionEvent(EventType.TASK_COMPLETED, "task-1", "done"));

        assertEquals(2, service.getEvents("task-1").size());
        assertEquals(2, service.getTaskSteps("task-1").size());
        assertEquals("editDocument", service.getTaskSteps("task-1").get(0).getAction());
        assertEquals("done", service.getTaskSteps("task-1").get(1).getResult());
    }
}
