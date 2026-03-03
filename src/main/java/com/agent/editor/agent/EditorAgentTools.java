package com.agent.editor.agent;

import com.agent.editor.dto.WebSocketMessage;
import com.agent.editor.model.AgentStepType;
import com.agent.editor.model.Document;
import com.agent.editor.websocket.WebSocketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
public class EditorAgentTools {
    private AgentState agentState;
    private WebSocketService webSocketService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool("Edit the document content with specified changes")
    public String editDocument(String content) {
        if (content != null && !content.isEmpty()) {
            Document document = agentState.getDocument();
            document.setContent(content);
            return "Document content edited successfully.";
        } else {
            return "No content provided to edit the document.";
        }
    }

    @Tool("Search for specific text in the document")
    public String searchContent(String pattern) {
        Document document = agentState.getDocument();
        String content = document.getContent();
        if (content != null && !content.isEmpty()) {
            boolean found = document.getContent().toLowerCase().contains(pattern.toLowerCase());
            return "Search for '" + pattern + "': " + (found ? "Found" : "Not found");
        } else {
            return "Document is empty. No content to search.";
        }
    }

    @Tool("Analyze the document for word count, line count, etc.")
    public String analyzeDocument() {
        Document document = agentState.getDocument();
        String content = document.getContent();
        int words = content != null ? content.split("\\s+").length : 0;
        int lines = content != null ? content.split("\n").length : 0;
        return "Words: " + words + ", Lines: " + lines + ", Chars: " + (content != null ? content.length() : 0);
    }

    @Tool("Terminate the current task immediately")
    public String terminateTask() {
        return "done";
    }

    @Tool("Send a message directly to the user")
    public String respondToUser(String message) {
        if (message == null || message.isEmpty()) {
            return "No message provided to send to the user.";
        }

        webSocketService.sendToSession(agentState.getSessionId(),
                WebSocketMessage.step(agentState.getTaskId(), AgentStepType.USER_MESSAGE, message));
        return "Send message to user successfully";
    }

    @Tool("Replace content in the document based on a pattern")
    public String replaceContent(String pattern, String replacement) {
        Document document = agentState.getDocument();
        String content = document.getContent();
        if (content != null && !content.isEmpty()) {
            String newContent = content.replaceAll(pattern, replacement);
            document.setContent(newContent);
            return "Content replaced successfully.";
        } else {
            return "Document is empty. No content to replace.";
        }
    }

    public String executeTool(ToolExecutionRequest request) {
        return new DefaultToolExecutor(this, request)
                .execute(request, UUID.randomUUID());
    }
}
