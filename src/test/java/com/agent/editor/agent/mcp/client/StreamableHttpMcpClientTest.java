package com.agent.editor.agent.mcp.client;

import com.agent.editor.agent.tool.RecoverableToolException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class StreamableHttpMcpClientTest {

    private static final String BASE_URL = "https://example.test/mcp";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldInitializeBeforeListingToolsAndReuseSessionId() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                BASE_URL,
                Map.of("Authorization", "Bearer test-key"),
                restTemplate,
                OBJECT_MAPPER
        );

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"initialize\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-123")
                        .body("""
                                {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"web-search","version":"1.0.0"}}}
                                """));

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Mcp-Session-Id", "session-123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"tools/list\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"jsonrpc":"2.0","id":"2","result":{"tools":[{"name":"webSearch","description":"Search the public web","inputSchema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}}]}}
                                """));

        assertThat(client.listTools())
                .extracting(McpToolDescriptor::getName)
                .containsExactly("webSearch");
        server.verify();
    }

    @Test
    void shouldCallRemoteToolWithConfiguredHeaders() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                BASE_URL,
                Map.of("Authorization", "Bearer test-key"),
                restTemplate,
                OBJECT_MAPPER
        );

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"initialize\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-123")
                        .body("""
                                {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"web-search","version":"1.0.0"}}}
                                """));

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Mcp-Session-Id", "session-123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"tools/call\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"name\":\"webSearch\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"query\":\"latest ai news\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"jsonrpc":"2.0","id":"2","result":{"content":[{"type":"text","text":"result text"}],"structuredContent":{"items":[{"title":"news"}]}}}
                                """));

        McpToolCallResult result = client.callTool("webSearch", "{\"query\":\"latest ai news\"}");

        assertThat(result.getText()).isEqualTo("result text");
        assertThat(result.getStructuredContent()).isNotNull();
        server.verify();
    }

    @Test
    void shouldThrowRecoverableToolExceptionWhenJsonRpcReturnsError() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        StreamableHttpMcpClient client = new StreamableHttpMcpClient(
                BASE_URL,
                Map.of("Authorization", "Bearer test-key"),
                restTemplate,
                OBJECT_MAPPER
        );

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"initialize\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Mcp-Session-Id", "session-123")
                        .body("""
                                {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"web-search","version":"1.0.0"}}}
                                """));

        server.expect(ExpectedCount.once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Mcp-Session-Id", "session-123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"method\":\"tools/call\"")))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"jsonrpc":"2.0","id":"2","error":{"code":-32001,"message":"upstream timeout"}}
                                """));

        assertThatThrownBy(() -> client.callTool("webSearch", "{\"query\":\"latest ai news\"}"))
                .isInstanceOf(RecoverableToolException.class)
                .hasMessageContaining("upstream timeout");
        server.verify();
    }
}
