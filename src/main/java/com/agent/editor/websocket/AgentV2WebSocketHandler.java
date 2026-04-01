package com.agent.editor.websocket;

import com.agent.editor.dto.AgentEventStreamMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AgentV2WebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(AgentV2WebSocketHandler.class);

    @Autowired
    private WebSocketService webSocketService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        webSocketService.registerV2Session(session);
        logger.info("Agent v2 WebSocket connection established: {}", session.getId());
        webSocketService.sendToV2Session(session.getId(), AgentEventStreamMessage.connected(session.getId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        webSocketService.unregisterV2Session(session);
        logger.info("Agent v2 WebSocket connection closed: {}, status: {}", session.getId(), status);
    }
}
