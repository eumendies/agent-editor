package com.agent.editor.agent.mcp.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root Spring configuration properties for externally configured MCP servers.
 */
@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "agent.mcp")
public class McpProperties {

    private Map<String, McpServerProperties> servers = new LinkedHashMap<>();

}
