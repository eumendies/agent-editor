package com.agent.editor.websocket;

import com.agent.editor.agent.v2.event.ExecutionEvent;
import com.agent.editor.dto.AgentEventStreamMessage;
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
    private final Map<String, WebSocketSession> v2Sessions = new ConcurrentHashMap<>();
    private final Map<String, String> v2SessionTasks = new ConcurrentHashMap<>();
    
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

    public void registerV2Session(WebSocketSession session) {
        v2Sessions.put(session.getId(), session);
        logger.info("Agent v2 WebSocket session registered: {}", session.getId());
    }

    public void unregisterV2Session(WebSocketSession session) {
        String taskId = v2SessionTasks.remove(session.getId());
        v2Sessions.remove(session.getId());
        logger.info("Agent v2 WebSocket session unregistered: {}, task: {}", session.getId(), taskId);
    }

    public void bindV2TaskToSession(String sessionId, String taskId) {
        v2SessionTasks.put(sessionId, taskId);
    }

    public void sendToSession(String sessionId, WebSocketMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        sendPayload(sessionId, session, message);
    }

    public void sendToTask(String taskId, WebSocketMessage message) {
        sessionTasks.forEach((sessionId, boundTaskId) -> {
            if (taskId.equals(boundTaskId)) {
                sendToSession(sessionId, message);
            }
        });
    }

    public void sendToV2Session(String sessionId, AgentEventStreamMessage message) {
        WebSocketSession session = v2Sessions.get(sessionId);
        sendPayload(sessionId, session, message);
    }

    public void sendEventToV2Task(String taskId, ExecutionEvent event) {
        AgentEventStreamMessage message = AgentEventStreamMessage.event(event);
        v2SessionTasks.forEach((sessionId, boundTaskId) -> {
            if (taskId.equals(boundTaskId)) {
                sendToV2Session(sessionId, message);
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

    private void sendPayload(String sessionId, WebSocketSession session, Object payload) {
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                synchronized (session) {
                    session.sendMessage(new TextMessage(json));
                }
                logger.debug("Message sent to session {}: {}", sessionId, payload.getClass().getSimpleName());
            } catch (IOException e) {
                logger.error("Error sending message to session {}", sessionId, e);
            }
        } else {
            logger.warn("Session not found or closed: {}", sessionId);
        }
    }
}
