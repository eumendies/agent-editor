package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class McpInitializeResult {

    private String protocolVersion;
    private JsonNode capabilities;
    private JsonNode serverInfo;
}
