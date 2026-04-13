package com.agent.editor.agent.mcp.client;

import java.util.List;

/**
 * Minimal MCP client abstraction used by the local tool runtime.
 * <p>
 * Implementations are responsible for discovering remote tools and executing
 * calls against a specific MCP transport, while the rest of the agent runtime
 * remains isolated from protocol details.
 */
public interface McpClient {

    /**
     * Lists the remote tools currently exposed by the configured MCP server.
     *
     * @return immutable remote tool descriptors for local registration
     */
    List<McpToolDescriptor> listTools();

    /**
     * Executes a remote MCP tool with the provided JSON arguments.
     *
     * @param toolName remote MCP tool name
     * @param argumentsJson serialized JSON arguments expected by the remote tool
     * @return normalized execution result that the local runtime can format
     */
    McpToolCallResult callTool(String toolName, String argumentsJson);
}
