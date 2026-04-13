package com.agent.editor.agent.mcp.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server entry declared under
 * {@code agent.mcp.servers.*}.
 */
@Data
@NoArgsConstructor
public class McpServerProperties {

    private String type;
    private String description;
    private boolean active;
    private String name;
    private String baseUrl;
    private Map<String, String> headers = new LinkedHashMap<>();
    private List<McpToolProperties> tools = new ArrayList<>();

}
