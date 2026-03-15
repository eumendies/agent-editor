package com.agent.editor.service;

import com.agent.editor.agent.v2.core.state.TaskState;
import com.agent.editor.agent.v2.core.state.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaskQueryServiceTest {

    @Test
    void shouldStoreAndReturnTaskState() {
        TaskQueryService service = new TaskQueryService();
        TaskState state = new TaskState("task-1", TaskStatus.COMPLETED, "final text");

        service.save(state);

        TaskState stored = service.findById("task-1");
        assertNotNull(stored);
        assertEquals(TaskStatus.COMPLETED, stored.status());
        assertEquals("final text", stored.finalContent());
    }
}
