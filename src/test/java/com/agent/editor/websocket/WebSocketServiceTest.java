package com.agent.editor.websocket;

import com.agent.editor.agent.event.EventType;
import com.agent.editor.agent.event.ExecutionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketServiceTest {

    @Test
    void shouldSendNativeEventEnvelopeToTaskSubscribers() throws Exception {
        WebSocketService service = new WebSocketService();
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        service.registerSession(session);
        service.bindTaskToSession("session-1", "task-1");

        service.sendEventToTask("task-1", new ExecutionEvent(EventType.TOOL_CALLED, "task-1", "editDocument"));

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("\"type\":\"EVENT\"")
                        && message.getPayload().contains("\"taskId\":\"task-1\"")
                        && message.getPayload().contains("\"message\":\"editDocument\"")
                        && !message.getPayload().contains("stepType")));
    }

    @Test
    void shouldSendTextStreamDeltaEnvelopeToTaskSubscribers() throws Exception {
        WebSocketService service = new WebSocketService();
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-stream");
        when(session.isOpen()).thenReturn(true);

        service.registerSession(session);
        service.bindTaskToSession("session-stream", "task-stream");

        service.sendEventToTask("task-stream", new ExecutionEvent(EventType.TEXT_STREAM_DELTA, "task-stream", "partial text"));

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("\"type\":\"EVENT\"")
                        && message.getPayload().contains("\"taskId\":\"task-stream\"")
                        && message.getPayload().contains("\"type\":\"TEXT_STREAM_DELTA\"")
                        && message.getPayload().contains("\"message\":\"partial text\"")));
    }
}
