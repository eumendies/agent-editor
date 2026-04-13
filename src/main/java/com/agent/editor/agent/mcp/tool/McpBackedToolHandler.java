package com.agent.editor.agent.mcp.tool;

import com.agent.editor.agent.mcp.client.McpClient;
import com.agent.editor.agent.mcp.client.McpToolCallResult;
import com.agent.editor.agent.mcp.client.McpToolDescriptor;
import com.agent.editor.agent.mcp.config.McpToolProperties;
import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolHandler;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class McpBackedToolHandler implements ToolHandler {

    private final McpToolProperties toolProperties;
    private final McpToolDescriptor remoteDescriptor;
    private final McpClient mcpClient;
    private final McpToolResultFormatter resultFormatter;

    public McpBackedToolHandler(McpToolProperties toolProperties,
                                McpToolDescriptor remoteDescriptor,
                                McpClient mcpClient,
                                McpToolResultFormatter resultFormatter) {
        this.toolProperties = toolProperties;
        this.remoteDescriptor = remoteDescriptor;
        this.mcpClient = mcpClient;
        this.resultFormatter = resultFormatter;
    }

    @Override
    public String name() {
        return toolProperties.getToolName();
    }

    @Override
    public ToolSpecification specification() {
        JsonObjectSchema parameters = remoteDescriptor.getToolSpecification() != null
                ? remoteDescriptor.getToolSpecification().parameters()
                : toJsonObjectSchema(remoteDescriptor.getInputSchema());
        return ToolSpecification.builder()
                // 本地 tool name 是权限控制和 prompt 里的稳定标识，不直接泄漏远端服务内部命名细节。
                .name(name())
                .description(resolveDescription())
                .parameters(parameters)
                .build();
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        McpToolCallResult result = mcpClient.callTool(
                toolProperties.getRemoteToolName(),
                invocation.getArguments()
        );
        // 远端工具级错误仍返回普通 ToolResult，让模型在下一轮读取错误内容后自行修正查询或参数。
        return new ToolResult(resultFormatter.format(result));
    }

    private String resolveDescription() {
        if (toolProperties.getDescription() != null && !toolProperties.getDescription().isBlank()) {
            return toolProperties.getDescription();
        }
        return remoteDescriptor.getDescription();
    }

    private JsonObjectSchema toJsonObjectSchema(JsonNode inputSchema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (inputSchema == null || inputSchema.isNull()) {
            return builder.build();
        }
        if (inputSchema.hasNonNull("description")) {
            builder.description(inputSchema.get("description").asText());
        }
        JsonNode propertiesNode = inputSchema.path("properties");
        if (propertiesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = propertiesNode.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                addProperty(builder, entry.getKey(), entry.getValue());
            }
        }
        JsonNode requiredNode = inputSchema.path("required");
        if (requiredNode.isArray()) {
            List<String> required = new ArrayList<>();
            for (JsonNode requiredEntry : requiredNode) {
                required.add(requiredEntry.asText());
            }
            builder.required(required);
        }
        return builder.build();
    }

    private void addProperty(JsonObjectSchema.Builder builder, String propertyName, JsonNode propertySchema) {
        String description = propertySchema.path("description").asText(null);
        String type = propertySchema.path("type").asText("");
        switch (type) {
            case "string" -> builder.addStringProperty(propertyName, description);
            case "integer" -> builder.addIntegerProperty(propertyName, description);
            case "number" -> builder.addNumberProperty(propertyName, description);
            case "boolean" -> builder.addBooleanProperty(propertyName, description);
            default -> builder.addProperty(propertyName, JsonObjectSchema.builder()
                    .description(description)
                    .additionalProperties(true)
                    .build());
        }
    }
}
