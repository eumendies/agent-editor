package com.agent.editor.dto;

import com.agent.editor.model.AgentStepType;

public class WebSocketMessage {
    private String type;
    private String taskId;
    private String sessionId;
    private AgentStepType stepType;
    private String content;
    private Object data;
    private long timestamp;

    public WebSocketMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public static WebSocketMessage step(String taskId, AgentStepType stepType, String content) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("STEP");
        message.setTaskId(taskId);
        message.setStepType(stepType);
        message.setContent(content);
        return message;
    }

    public static WebSocketMessage completed(String taskId, String result) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("COMPLETED");
        message.setTaskId(taskId);
        message.setContent(result);
        return message;
    }

    public static WebSocketMessage error(String taskId, String error) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("ERROR");
        message.setTaskId(taskId);
        message.setContent(error);
        return message;
    }

    public static WebSocketMessage progress(String taskId, int currentStep, int totalSteps) {
        WebSocketMessage message = new WebSocketMessage();
        message.setType("PROGRESS");
        message.setTaskId(taskId);
        message.setContent(String.format("Step %d of %d", currentStep, totalSteps));
        return message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AgentStepType getStepType() {
        return stepType;
    }

    public void setStepType(AgentStepType stepType) {
        this.stepType = stepType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
