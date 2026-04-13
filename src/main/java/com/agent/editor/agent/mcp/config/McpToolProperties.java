package com.agent.editor.agent.mcp.config;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local exposure settings for a specific remote MCP tool.
 */
@Data
@NoArgsConstructor
public class McpToolProperties {

    private String toolName;
    private String remoteToolName;
    private String description;

}
