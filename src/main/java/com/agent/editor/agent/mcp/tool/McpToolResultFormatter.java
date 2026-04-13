package com.agent.editor.agent.mcp.tool;

import com.agent.editor.agent.mcp.client.McpToolCallResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Formats normalized MCP tool results into the plain string payload consumed by
 * the existing tool loop runtime.
 */
public class McpToolResultFormatter {

    private final ObjectMapper objectMapper;

    /**
     * @param objectMapper mapper used to serialize MCP structured results
     */
    public McpToolResultFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts structured MCP output into a stable JSON wrapper while preserving
     * plain text-only results as-is.
     *
     * @param result normalized MCP call result
     * @return string payload to place into {@code ToolResult.message}
     */
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
