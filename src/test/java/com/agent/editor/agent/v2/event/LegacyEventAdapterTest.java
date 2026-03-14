package com.agent.editor.agent.v2.event;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.AgentStepType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyEventAdapterTest {

    @Test
    void shouldMapToolCalledEventToLegacyStep() {
        LegacyEventAdapter adapter = new LegacyEventAdapter();

        AgentStep step = adapter.toStep(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"), 2);

        assertEquals("task-1", step.getTaskId());
        assertEquals(2, step.getStepNumber());
        assertEquals(AgentStepType.ACTION, step.getType());
        assertEquals("editDocument", step.getAction());
    }

    @Test
    void shouldMapCompletionEventToLegacyWebSocketMessage() {
        LegacyEventAdapter adapter = new LegacyEventAdapter();

        WebSocketMessage message = adapter.toWebSocketMessage(new ExecutionEvent(EventType.TASK_COMPLETED, "task-1", "done"));

        assertEquals("COMPLETED", message.getType());
        assertEquals("task-1", message.getTaskId());
        assertEquals("done", message.getContent());
    }
}
