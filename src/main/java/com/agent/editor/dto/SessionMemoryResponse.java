package com.agent.editor.dto;

import java.util.ArrayList;
import java.util.List;

public class SessionMemoryResponse {

    private String sessionId;
    private int messageCount;
    private List<SessionMemoryMessageResponse> messages = new ArrayList<>();

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public List<SessionMemoryMessageResponse> getMessages() {
        return messages;
    }

    public void setMessages(List<SessionMemoryMessageResponse> messages) {
        this.messages = messages;
    }
}
