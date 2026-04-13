package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Normalized remote MCP execution payload consumed by the local tool loop.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolCallResult {

    private boolean error;
    private JsonNode structuredContent;
    private String text;
}
