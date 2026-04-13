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

/**
 * Local {@link ToolHandler} wrapper around a remote MCP tool.
 * <p>
 * This class is responsible for applying local naming/description overrides,
 * exposing the remote input schema to the model, and mapping remote execution
 * results back into the existing tool loop contract.
 */
public class McpBackedToolHandler implements ToolHandler {

    private final McpToolProperties toolProperties;
    private final McpToolDescriptor remoteDescriptor;
    private final McpClient mcpClient;
    private final McpToolResultFormatter resultFormatter;

    /**
     * @param toolProperties local exposure settings such as tool name and description
     * @param remoteDescriptor normalized remote MCP tool descriptor
     * @param mcpClient client used to invoke the remote MCP tool
     * @param resultFormatter formatter that converts MCP results into tool loop messages
     */
    public McpBackedToolHandler(McpToolProperties toolProperties,
                                McpToolDescriptor remoteDescriptor,
                                McpClient mcpClient,
                                McpToolResultFormatter resultFormatter) {
        this.toolProperties = toolProperties;
        this.remoteDescriptor = remoteDescriptor;
        this.mcpClient = mcpClient;
        this.resultFormatter = resultFormatter;
    }

    /**
     * @return locally exposed tool name used by prompts and access policies
     */
    @Override
    public String name() {
        return toolProperties.getToolName();
    }

    /**
     * Builds the tool specification visible to the model.
     * <p>
     * When the SDK already supplied a parsed {@link ToolSpecification}, that
     * schema is reused directly; otherwise the legacy JSON schema fallback is
     * converted back into LangChain4j's schema model.
     *
     * @return locally named tool specification
     */
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

    /**
     * Executes the remote MCP tool and converts the response into the
     * project-wide {@link ToolResult} contract.
     *
     * @param invocation local tool invocation carrying serialized JSON arguments
     * @param context current tool execution context
     * @return formatted tool result visible to the model
     */
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
        // 旧JSON schema回退路径只保留当前项目真正需要的基础类型，避免在SDK迁移后继续扩大手写schema逻辑面。
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
