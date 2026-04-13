package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Snapshot of a remote MCP tool definition after local normalization.
 * <p>
 * The descriptor keeps both the legacy JSON schema view used by older code
 * paths and the original SDK {@link ToolSpecification} when available.
 */
@Data
@NoArgsConstructor
public class McpToolDescriptor {

    private String name;
    private String description;
    private JsonNode inputSchema;
    private ToolSpecification toolSpecification;

    /**
     * Creates a descriptor backed only by JSON schema data.
     *
     * @param name remote tool name
     * @param description remote tool description
     * @param inputSchema normalized input schema
     */
    public McpToolDescriptor(String name,
                             String description,
                             JsonNode inputSchema) {
        this(name, description, inputSchema, null);
    }

    /**
     * Creates a descriptor that optionally keeps the SDK tool specification.
     *
     * @param name remote tool name
     * @param description remote tool description
     * @param inputSchema normalized input schema
     * @param toolSpecification original SDK specification, if already available
     */
    public McpToolDescriptor(String name,
                             String description,
                             JsonNode inputSchema,
                             ToolSpecification toolSpecification) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.toolSpecification = toolSpecification;
    }
}
