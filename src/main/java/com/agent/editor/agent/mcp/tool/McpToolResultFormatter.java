package com.agent.editor.agent.mcp.tool;

import com.agent.editor.agent.mcp.client.McpToolCallResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class McpToolResultFormatter {

    private final ObjectMapper objectMapper;

    public McpToolResultFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String format(McpToolCallResult result) {
        try {
            if (result.getStructuredContent() != null) {
                ObjectNode payload = objectMapper.createObjectNode();
                payload.set("structuredContent", result.getStructuredContent());
                if (result.getText() != null && !result.getText().isBlank()) {
                    payload.put("text", result.getText());
                }
                return objectMapper.writeValueAsString(payload);
            }
            return result.getText();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Failed to serialize MCP tool result", exception);
        }
    }
}
