package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class McpJsonRpcResponse {

    private String jsonrpc;
    private String id;
    private JsonNode result;
    private JsonRpcError error;

    @Data
    @NoArgsConstructor
    public static class JsonRpcError {
        private Integer code;
        private String message;
        private JsonNode data;
    }
}
