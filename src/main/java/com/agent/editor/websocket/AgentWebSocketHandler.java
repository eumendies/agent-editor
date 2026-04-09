package com.agent.editor.websocket;

import com.agent.editor.dto.AgentEventStreamMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    @Autowired
    private WebSocketService webSocketService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        webSocketService.registerSession(session);
        logger.info("Agent WebSocket connection established: {}", session.getId());
        webSocketService.sendToSession(session.getId(), AgentEventStreamMessage.connected(session.getId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        webSocketService.unregisterSession(session);
        logger.info("Agent WebSocket connection closed: {}, status: {}", session.getId(), status);
    }
}
