package com.agent.editor.websocket;

import com.agent.editor.agent.event.ExecutionEvent;
import com.agent.editor.dto.AgentEventStreamMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
        logger.info("Agent WebSocket session registered: {}", session.getId());
    }

    public void unregisterSession(WebSocketSession session) {
        String taskId = sessionTasks.remove(session.getId());
        sessions.remove(session.getId());
        logger.info("Agent WebSocket session unregistered: {}, task: {}", session.getId(), taskId);
    }

    public void bindTaskToSession(String sessionId, String taskId) {
        sessionTasks.put(sessionId, taskId);
    }

    public void unbindTaskFromSession(String sessionId, String taskId) {
        sessionTasks.computeIfPresent(sessionId, (ignored, boundTaskId) -> taskId.equals(boundTaskId) ? null : boundTaskId);
    }

    public void sendToSession(String sessionId, AgentEventStreamMessage message) {
        WebSocketSession session = sessions.get(sessionId);
        sendPayload(sessionId, session, message);
    }

    public void sendEventToTask(String taskId, ExecutionEvent event) {
        AgentEventStreamMessage message = AgentEventStreamMessage.event(event);
        sessionTasks.forEach((sessionId, boundTaskId) -> {
            if (taskId.equals(boundTaskId)) {
                sendToSession(sessionId, message);
            }
        });
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
