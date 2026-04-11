package com.agent.editor.agent.mcp.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class McpToolProperties {

    private String toolName;
    private String remoteToolName;
    private String description;

}
