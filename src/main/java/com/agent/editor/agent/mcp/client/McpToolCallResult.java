package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpToolCallResult {

    private boolean error;
    private JsonNode structuredContent;
    private String text;
}
