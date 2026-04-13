package com.agent.editor.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class McpToolDescriptor {

    private String name;
    private String description;
    private JsonNode inputSchema;
    private ToolSpecification toolSpecification;

    public McpToolDescriptor(String name,
                             String description,
                             JsonNode inputSchema) {
        this(name, description, inputSchema, null);
    }

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
