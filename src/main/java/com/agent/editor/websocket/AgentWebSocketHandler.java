package com.agent.editor.websocket;

import com.agent.editor.dto.WebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentWebSocketHandler.class);
    
    @Autowired
    private WebSocketService webSocketService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    private final Map<String, String> sessionInfoMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        webSocketService.registerSession(session);
        
        String sessionId = session.getId();
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        
        if (query != null && query.contains("taskId=")) {
            String taskId = extractParameter(query, "taskId");
            webSocketService.bindTaskToSession(sessionId, taskId);
            sessionInfoMap.put(sessionId, taskId);
        }
        
        logger.info("WebSocket connection established: {}", sessionId);
        
        WebSocketMessage welcomeMessage = new WebSocketMessage();
        welcomeMessage.setType("CONNECTED");
        welcomeMessage.setSessionId(sessionId);
        welcomeMessage.setContent("Connected to AI Editor Agent");
        
        webSocketService.sendToSession(sessionId, welcomeMessage);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.debug("Received message from {}: {}", session.getId(), payload);
        
        try {
            WebSocketMessage request = objectMapper.readValue(payload, WebSocketMessage.class);
            
            if ("PING".equals(request.getType())) {
                WebSocketMessage response = new WebSocketMessage();
                response.setType("PONG");
                response.setSessionId(session.getId());
                webSocketService.sendToSession(session.getId(), response);
            } else if ("SUBSCRIBE".equals(request.getType())) {
                String taskId = request.getTaskId();
                if (taskId != null) {
                    webSocketService.bindTaskToSession(session.getId(), taskId);
                    sessionInfoMap.put(session.getId(), taskId);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error handling message", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        webSocketService.unregisterSession(session);
        sessionInfoMap.remove(session.getId());
        logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for session: {}", session.getId(), exception);
        webSocketService.unregisterSession(session);
    }

    private String extractParameter(String query, String paramName) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(paramName)) {
                return keyValue[1];
            }
        }
        return null;
    }
}
