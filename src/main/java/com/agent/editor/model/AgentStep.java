package com.agent.editor.model;

import java.time.LocalDateTime;
import java.util.Map;

public class AgentStep {
    private String id;
    private String taskId;
    private int stepNumber;
    private AgentStepType type;
    private String thought;
    private String action;
    private String observation;
    private String result;
    private String error;
    private Map<String, Object> metadata;
    private LocalDateTime timestamp;
    private boolean isFinal;

    public AgentStep() {
        this.timestamp = LocalDateTime.now();
    }

    public AgentStep(String id, String taskId, int stepNumber, AgentStepType type) {
        this.id = id;
        this.taskId = taskId;
        this.stepNumber = stepNumber;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public AgentStepType getType() {
        return type;
    }

    public void setType(AgentStepType type) {
        this.type = type;
    }

    public String getThought() {
        return thought;
    }

    public void setThought(String thought) {
        this.thought = thought;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getObservation() {
        return observation;
    }

    public void setObservation(String observation) {
        this.observation = observation;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }
}
