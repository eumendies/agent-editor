package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class McpJsonRpcRequest {

    private String jsonrpc;
    private String id;
    private String method;
    private JsonNode params;
}
