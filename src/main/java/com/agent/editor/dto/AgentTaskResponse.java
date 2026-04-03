package com.agent.editor.dto;

import com.agent.editor.model.AgentStep;
import java.time.LocalDateTime;
import java.util.List;

public class AgentTaskResponse {
    private String taskId;
    private String documentId;
    private String status;
    private AgentStep currentStep;
    private String finalResult;
    private int totalSteps;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<LongTermMemoryCandidateResponse> pendingMemoryCandidates = List.of();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public AgentStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(AgentStep currentStep) {
        this.currentStep = currentStep;
    }

    public String getFinalResult() {
        return finalResult;
    }

    public void setFinalResult(String finalResult) {
        this.finalResult = finalResult;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public void setTotalSteps(int totalSteps) {
        this.totalSteps = totalSteps;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public List<LongTermMemoryCandidateResponse> getPendingMemoryCandidates() {
        return pendingMemoryCandidates;
    }

    public void setPendingMemoryCandidates(List<LongTermMemoryCandidateResponse> pendingMemoryCandidates) {
        this.pendingMemoryCandidates = pendingMemoryCandidates == null ? List.of() : List.copyOf(pendingMemoryCandidates);
    }
}
