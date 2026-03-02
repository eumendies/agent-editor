package com.agent.editor.agent;

import com.agent.editor.model.AgentMode;
import com.agent.editor.model.AgentStep;
import com.agent.editor.model.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AgentState {
    private String taskId;
    private String sessionId;
    private Document document;
    private String instruction;
    private AgentMode mode;
    private List<AgentStep> steps;
    private int currentStep;
    private int maxSteps;
    private String status;
    private String currentThought;
    private String currentAction;
    private String currentObservation;
    private boolean isCompleted;
    private long startTime;
    private long endTime;

    public AgentState() {
        this.taskId = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.currentStep = 0;
        this.status = "PENDING";
        this.maxSteps = 10;
    }

    public AgentState(Document document, String instruction, AgentMode mode) {
        this();
        this.document = document;
        this.instruction = instruction;
        this.mode = mode;
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

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public AgentMode getMode() {
        return mode;
    }

    public void setMode(AgentMode mode) {
        this.mode = mode;
    }

    public List<AgentStep> getSteps() {
        return steps;
    }

    public void setSteps(List<AgentStep> steps) {
        this.steps = steps;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
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

    public String getCurrentThought() {
        return currentThought;
    }

    public void setCurrentThought(String currentThought) {
        this.currentThought = currentThought;
    }

    public String getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(String currentAction) {
        this.currentAction = currentAction;
    }

    public String getCurrentObservation() {
        return currentObservation;
    }

    public void setCurrentObservation(String currentObservation) {
        this.currentObservation = currentObservation;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
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
