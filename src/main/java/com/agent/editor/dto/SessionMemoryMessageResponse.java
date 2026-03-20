package com.agent.editor.dto;

import java.util.ArrayList;
import java.util.List;

public class SessionMemoryMessageResponse {

    private String type;
    private String text;
    private String toolCallId;
    private String toolName;
    private String arguments;
    private List<SessionMemoryToolCallResponse> toolCalls = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public List<SessionMemoryToolCallResponse> getToolCalls() {
        return toolCalls;
    }

    public void setToolCalls(List<SessionMemoryToolCallResponse> toolCalls) {
        this.toolCalls = toolCalls;
    }
}
