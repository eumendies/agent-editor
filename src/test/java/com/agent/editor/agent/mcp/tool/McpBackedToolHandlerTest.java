package com.agent.editor.agent.mcp.tool;

import com.agent.editor.agent.mcp.client.McpClient;
import com.agent.editor.agent.mcp.client.McpToolCallResult;
import com.agent.editor.agent.mcp.client.McpToolDescriptor;
import com.agent.editor.agent.mcp.config.McpToolProperties;
import com.agent.editor.agent.tool.ToolContext;
import com.agent.editor.agent.tool.ToolInvocation;
import com.agent.editor.agent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpBackedToolHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldExposeConfiguredLocalToolNameAndRemoteSchemaAsToolSpecification() {
        McpClient client = mock(McpClient.class);
        McpBackedToolHandler handler = new McpBackedToolHandler(
                toolProperties("webSearch", "webSearch", "Search real-time public web information"),
                new McpToolDescriptor("webSearch", "Remote tool description", inputSchema()),
                client,
                new McpToolResultFormatter(OBJECT_MAPPER)
        );

        ToolSpecification specification = handler.specification();

        assertThat(specification.name()).isEqualTo("webSearch");
        assertThat(specification.description()).isEqualTo("Search real-time public web information");
        assertThat(specification.parameters()).isInstanceOf(JsonObjectSchema.class);
        assertThat(specification.parameters().properties()).containsKey("query");
        assertThat(specification.parameters().required()).containsExactly("query");
    }

    @Test
    void shouldReuseSdkToolSpecificationParametersWhenAvailable() {
        McpClient client = mock(McpClient.class);
        ToolSpecification remoteSpecification = ToolSpecification.builder()
                .name("webSearch")
                .description("Remote tool description")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "Search query")
                        .required("query")
                        .build())
                .build();
        McpBackedToolHandler handler = new McpBackedToolHandler(
                toolProperties("webSearch", "webSearch", "Search real-time public web information"),
                new McpToolDescriptor("webSearch", "Remote tool description", inputSchema(), remoteSpecification),
                client,
                new McpToolResultFormatter(OBJECT_MAPPER)
        );

        ToolSpecification specification = handler.specification();

        assertThat(specification.name()).isEqualTo("webSearch");
        assertThat(specification.description()).isEqualTo("Search real-time public web information");
        assertThat(specification.parameters()).isSameAs(remoteSpecification.parameters());
    }

    @Test
    void shouldConvertStructuredContentAndTextIntoToolResultMessage() {
        McpClient client = mock(McpClient.class);
        when(client.callTool("webSearch", "{\"query\":\"latest ai news\"}"))
                .thenReturn(new McpToolCallResult(
                        false,
                        OBJECT_MAPPER.createObjectNode().putArray("items").addObject().put("title", "news"),
                        "result text"
                ));
        McpBackedToolHandler handler = new McpBackedToolHandler(
                toolProperties("webSearch", "webSearch", "Search real-time public web information"),
                new McpToolDescriptor("webSearch", "Remote tool description", inputSchema()),
                client,
                new McpToolResultFormatter(OBJECT_MAPPER)
        );

        ToolResult result = handler.execute(
                new ToolInvocation("webSearch", "{\"query\":\"latest ai news\"}"),
                new ToolContext("task-1", null)
        );

        assertThat(result.getUpdatedContent()).isNull();
        assertThat(result.getMessage()).contains("\"structuredContent\"");
        assertThat(result.getMessage()).contains("\"title\":\"news\"");
        assertThat(result.getMessage()).contains("\"text\":\"result text\"");
    }

    @Test
    void shouldSurfaceRemoteToolLevelErrorAsNormalToolResult() {
        McpClient client = mock(McpClient.class);
        ObjectNode structuredContent = OBJECT_MAPPER.createObjectNode();
        structuredContent.put("status", "error");
        when(client.callTool("webSearch", "{\"query\":\"\"}"))
                .thenReturn(new McpToolCallResult(true, structuredContent, "query is required"));
        McpBackedToolHandler handler = new McpBackedToolHandler(
                toolProperties("webSearch", "webSearch", "Search real-time public web information"),
                new McpToolDescriptor("webSearch", "Remote tool description", inputSchema()),
                client,
                new McpToolResultFormatter(OBJECT_MAPPER)
        );

        ToolResult result = handler.execute(
                new ToolInvocation("webSearch", "{\"query\":\"\"}"),
                new ToolContext("task-1", null)
        );

        assertThat(result.getMessage()).contains("\"status\":\"error\"");
        assertThat(result.getMessage()).contains("\"text\":\"query is required\"");
    }

    private McpToolProperties toolProperties(String toolName, String remoteToolName, String description) {
        McpToolProperties properties = new McpToolProperties();
        properties.setToolName(toolName);
        properties.setRemoteToolName(remoteToolName);
        properties.setDescription(description);
        return properties;
    }

    private ObjectNode inputSchema() {
        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string").put("description", "Search query");
        schema.putArray("required").add("query");
        return schema;
    }
}
