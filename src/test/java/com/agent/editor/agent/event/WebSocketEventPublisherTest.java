package com.agent.editor.agent.event;

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
        WebSocketEventPublisher publisher = new WebSocketEventPublisher(taskQueryService, webSocketService);
        ExecutionEvent event = new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument");

        publisher.publish(event);

        verify(webSocketService).sendEventToTask(
                org.mockito.ArgumentMatchers.eq("task-1"),
                argThat(publishedEvent ->
                        EventType.TOOL_CALLED == publishedEvent.getType()
                                && "task-1".equals(publishedEvent.getTaskId())
                                && "editDocument".equals(publishedEvent.getMessage()))
        );
    }

    @Test
    void shouldForwardTextStreamDeltaEventsWithoutTranslation() {
        TaskQueryService taskQueryService = new TaskQueryService();
        WebSocketService webSocketService = mock(WebSocketService.class);
        WebSocketEventPublisher publisher = new WebSocketEventPublisher(taskQueryService, webSocketService);
        ExecutionEvent event = new ExecutionEvent(EventType.TEXT_STREAM_DELTA, "task-stream", "partial text");

        publisher.publish(event);

        verify(webSocketService).sendEventToTask(
                org.mockito.ArgumentMatchers.eq("task-stream"),
                argThat(publishedEvent ->
                        EventType.TEXT_STREAM_DELTA == publishedEvent.getType()
                                && "task-stream".equals(publishedEvent.getTaskId())
                                && "partial text".equals(publishedEvent.getMessage()))
        );
    }
}
