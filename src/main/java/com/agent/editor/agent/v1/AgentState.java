package com.agent.editor.agent.v1;

import com.agent.editor.model.AgentMode;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentState {
    
    private final String taskId;
    private String sessionId;
    private final Document document;
    private final String instruction;
    private final AgentMode mode;
    private final List<AgentStep> steps;
    private int currentStep;
    private int maxSteps;
    private String status;
    private boolean completed;
    private long startTime;
    private long endTime;

    public AgentState(Document document, String instruction, AgentMode mode) {
        this.taskId = UUID.randomUUID().toString();
        this.document = document;
        this.instruction = instruction;
        this.mode = mode;
        this.steps = new ArrayList<>();
        this.currentStep = 0;
        this.status = "PENDING";
        this.maxSteps = 10;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Document getDocument() {
        return document;
    }

    public String getInstruction() {
        return instruction;
    }

    public AgentMode getMode() {
        return mode;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void incrementStep() {
        this.currentStep++;
    }

    public void addStep(AgentStep step) {
        this.steps.add(step);
    }
}
