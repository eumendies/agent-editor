package com.agent.editor.dto;

import java.time.LocalDateTime;

public class DiffResult {
    private String originalContent;
    private String modifiedContent;
    private String diffHtml;
    private int additions;
    private int deletions;
    private LocalDateTime timestamp;

    public DiffResult() {
        this.timestamp = LocalDateTime.now();
    }

    public DiffResult(String originalContent, String modifiedContent, String diffHtml) {
        this.originalContent = originalContent;
        this.modifiedContent = modifiedContent;
        this.diffHtml = diffHtml;
        this.timestamp = LocalDateTime.now();
        calculateStats();
    }

    private void calculateStats() {
        String[] originalLines = originalContent != null ? originalContent.split("\n") : new String[0];
        String[] modifiedLines = modifiedContent != null ? modifiedContent.split("\n") : new String[0];
        
        additions = modifiedLines.length - originalLines.length;
        deletions = additions < 0 ? -additions : 0;
        additions = additions > 0 ? additions : 0;
    }

    public String getOriginalContent() {
        return originalContent;
    }

    public void setOriginalContent(String originalContent) {
        this.originalContent = originalContent;
    }

    public String getModifiedContent() {
        return modifiedContent;
    }

    public void setModifiedContent(String modifiedContent) {
        this.modifiedContent = modifiedContent;
    }

    public String getDiffHtml() {
        return diffHtml;
    }

    public void setDiffHtml(String diffHtml) {
        this.diffHtml = diffHtml;
    }

    public int getAdditions() {
        return additions;
    }

    public void setAdditions(int additions) {
        this.additions = additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public void setDeletions(int deletions) {
        this.deletions = deletions;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
