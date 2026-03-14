package com.agent.editor.agent.v2.event;

import com.agent.editor.service.TaskQueryService;
import com.agent.editor.websocket.WebSocketService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebSocketEventPublisherTest {

    @Test
    void shouldPersistEventAndForwardToTaskWebSocketChannel() {
        TaskQueryService taskQueryService = new TaskQueryService();
        WebSocketService webSocketService = mock(WebSocketService.class);
        WebSocketEventPublisher publisher = new WebSocketEventPublisher(taskQueryService, webSocketService, new LegacyEventAdapter());
        ExecutionEvent event = new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument");

        publisher.publish(event);

        verify(webSocketService).sendToTask(
                org.mockito.ArgumentMatchers.eq("task-1"),
                argThat(message ->
                        "STEP".equals(message.getType())
                                && "task-1".equals(message.getTaskId())
                                && "editDocument".equals(message.getContent()))
        );
    }
}
