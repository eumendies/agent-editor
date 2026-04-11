package com.agent.editor.agent.mcp.config;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@ConfigurationProperties(prefix = "agent.mcp")
public class McpProperties {

    private Map<String, McpServerProperties> servers = new LinkedHashMap<>();

}
