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

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTasks = new ConcurrentHashMap<>();
    
    @Autowired
    private ObjectMapper objectMapper;

    public void registerSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
        logger.info("WebSocket session registered: {}", session.getId());
    }

    public void unregisterSession(WebSocketSession session) {
        String taskId = sessionTasks.remove(session.getId());
        sessions.remove(session.getId());
        logger.info("WebSocket session unregistered: {}, task: {}", session.getId(), taskId);
    }

    public void bindTaskToSession(String sessionId, String taskId) {
        sessionTasks.put(sessionId, taskId);
    }

    public void sendToSession(String sessionId, WebSocketMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                logger.debug("Message sent to session {}: {}", sessionId, message.getType());
            } catch (IOException e) {
                logger.error("Error sending message to session {}", sessionId, e);
            }
        } else {
            logger.warn("Session not found or closed: {}", sessionId);
        }
    }

    public void sendToTask(String taskId, WebSocketMessage message) {
        sessionTasks.forEach((sessionId, boundTaskId) -> {
            if (taskId.equals(boundTaskId)) {
                sendToSession(sessionId, message);
            }
        });
    }

    public void broadcast(WebSocketMessage message) {
        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            logger.error("Error serializing message", e);
            return;
        }
        
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (IOException e) {
                    logger.error("Error broadcasting to session {}", session.getId(), e);
                }
            }
        }
    }

    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean isSessionActive(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        return session != null && session.isOpen();
    }

    public int getActiveSessionCount() {
        return (int) sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
    }
}
