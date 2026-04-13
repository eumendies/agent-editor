package com.agent.editor.agent.mcp.client;

import com.agent.editor.agent.tool.RecoverableToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SdkMcpClientAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldExposeSdkListedToolsAsLocalDescriptors() {
        dev.langchain4j.mcp.client.McpClient sdkClient = mock(dev.langchain4j.mcp.client.McpClient.class);
        when(sdkClient.listTools()).thenReturn(List.of(
                ToolSpecification.builder()
                        .name("webSearch")
                        .description("Search the public web")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("query")
                                .required("query")
                                .build())
                        .build()
        ));

        SdkMcpClientAdapter adapter = new SdkMcpClientAdapter(sdkClient, OBJECT_MAPPER);

        assertThat(adapter.listTools())
                .extracting(McpToolDescriptor::getName)
                .containsExactly("webSearch");
    }

    @Test
    void shouldNormalizeSdkStructuredResultAndText() {
        dev.langchain4j.mcp.client.McpClient sdkClient = mock(dev.langchain4j.mcp.client.McpClient.class);
        when(sdkClient.executeTool(any())).thenReturn(ToolExecutionResult.builder()
                .result(Map.of("items", List.of(Map.of("title", "news"))))
                .resultText("result text")
                .build());

        SdkMcpClientAdapter adapter = new SdkMcpClientAdapter(sdkClient, OBJECT_MAPPER);
        McpToolCallResult result = adapter.callTool("webSearch", "{\"query\":\"latest ai news\"}");

        assertThat(result.getStructuredContent()).isNotNull();
        assertThat(result.getText()).isEqualTo("result text");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void shouldWrapSdkExecutionFailuresAsRecoverableToolException() {
        dev.langchain4j.mcp.client.McpClient sdkClient = mock(dev.langchain4j.mcp.client.McpClient.class);
        when(sdkClient.executeTool(any())).thenThrow(new RuntimeException("timeout"));

        SdkMcpClientAdapter adapter = new SdkMcpClientAdapter(sdkClient, OBJECT_MAPPER);

        assertThatThrownBy(() -> adapter.callTool("webSearch", "{\"query\":\"latest ai news\"}"))
                .isInstanceOf(RecoverableToolException.class)
                .hasMessageContaining("timeout");
    }
}
