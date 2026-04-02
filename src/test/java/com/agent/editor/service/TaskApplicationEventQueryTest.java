package com.agent.editor.service;

import com.agent.editor.agent.v2.event.EventPublisher;
import com.agent.editor.agent.v2.event.EventType;
import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.agent.v2.task.TaskOrchestrator;
import com.agent.editor.websocket.WebSocketService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class TaskApplicationEventQueryTest {

    @Test
    void shouldExposeStoredExecutionEvents() {
        DocumentService documentService = new DocumentService();
        TaskQueryService queryService = new TaskQueryService();
        DiffService diffService = new DiffService();
        TaskOrchestrator orchestrator = mock(TaskOrchestrator.class);
        TaskApplicationService service = new TaskApplicationService(
                documentService,
                queryService,
                diffService,
                orchestrator,
                mock(WebSocketService.class),
                mock(EventPublisher.class),
                directTaskExecutor()
        );

        queryService.appendEvent(new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));

        List<ExecutionEvent> events = service.getTaskEvents("task-1");

        assertEquals(1, events.size());
        assertEquals(EventType.TOOL_CALLED, events.get(0).getType());
        assertEquals("editDocument", events.get(0).getMessage());
    }

    private static TaskExecutor directTaskExecutor() {
        return Runnable::run;
    }
}
