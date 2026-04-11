package com.agent.editor.agent.mcp.client;

import java.util.List;

public interface McpClient {

    List<McpToolDescriptor> listTools();

    McpToolCallResult callTool(String toolName, String argumentsJson);
}
