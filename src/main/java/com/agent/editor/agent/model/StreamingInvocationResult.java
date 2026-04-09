package com.agent.editor.agent.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StreamingInvocationResult {

    private final ChatResponse chatResponse;
    private final String text;
    private final List<ToolExecutionRequest> toolExecutionRequests;

    public StreamingInvocationResult(ChatResponse chatResponse,
                                     String text,
                                     List<ToolExecutionRequest> toolExecutionRequests) {
        this.chatResponse = chatResponse;
        this.text = text == null ? "" : text;
        this.toolExecutionRequests = toolExecutionRequests == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(toolExecutionRequests));
    }

    public ChatResponse getChatResponse() {
        return chatResponse;
    }

    public String getText() {
        return text;
    }

    public List<ToolExecutionRequest> getToolExecutionRequests() {
        return toolExecutionRequests;
    }
}
