package com.agent.editor.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentV2WebSocketHandlerTest {

    @Test
    void shouldSendConnectedEnvelopeWhenConnectionEstablished() throws Exception {
        WebSocketService webSocketService = new WebSocketService();
        ReflectionTestUtils.setField(webSocketService, "objectMapper", new ObjectMapper());
        AgentV2WebSocketHandler handler = new AgentV2WebSocketHandler();
        ReflectionTestUtils.setField(handler, "webSocketService", webSocketService);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-v2-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(argThat((TextMessage message) ->
                message.getPayload().contains("\"type\":\"CONNECTED\"")
                        && message.getPayload().contains("\"sessionId\":\"session-v2-1\"")));
    }
}
