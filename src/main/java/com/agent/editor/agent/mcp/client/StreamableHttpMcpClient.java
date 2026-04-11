package com.agent.editor.agent.mcp.client;

import com.agent.editor.agent.tool.RecoverableToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamableHttpMcpClient implements McpClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final String MCP_SESSION_ID_HEADER = "Mcp-Session-Id";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final String baseUrl;
    private final Map<String, String> headers;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicInteger requestIdSequence = new AtomicInteger(1);

    private String sessionId;
    private boolean initialized;

    public StreamableHttpMcpClient(String baseUrl,
                                   Map<String, String> headers,
                                   RestTemplate restTemplate,
                                   ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<McpToolDescriptor> listTools() {
        ensureInitialized();
        McpJsonRpcResponse response = invoke("tools/list", objectMapper.createObjectNode());
        ArrayNode tools = requireResultObject(response).withArray("tools");
        List<McpToolDescriptor> descriptors = new ArrayList<>();
        for (JsonNode tool : tools) {
            descriptors.add(new McpToolDescriptor(
                    tool.path("name").asText(),
                    tool.path("description").asText(null),
                    tool.path("inputSchema").isMissingNode() ? null : tool.path("inputSchema")
            ));
        }
        return List.copyOf(descriptors);
    }

    @Override
    public McpToolCallResult callTool(String toolName, String argumentsJson) {
        ensureInitialized();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", parseArguments(argumentsJson));

        McpJsonRpcResponse response = invoke("tools/call", params);
        JsonNode result = requireResultObject(response);
        return new McpToolCallResult(
                result.path("isError").asBoolean(false),
                result.path("structuredContent").isMissingNode() ? null : result.path("structuredContent"),
                extractText(result.path("content"))
        );
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", objectMapper.createObjectNode());

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "ai-editor");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        // streamable-http MCP 首次必须先 initialize；后续请求复用同一 session，避免每次工具调用重新协商。
        invoke("initialize", params);
        initialized = true;
    }

    private McpJsonRpcResponse invoke(String method, JsonNode params) {
        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    baseUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(new McpJsonRpcRequest(
                            JSON_RPC_VERSION,
                            String.valueOf(requestIdSequence.getAndIncrement()),
                            method,
                            params
                    )), createHeaders()),
                    String.class
            );
            cacheSessionId(responseEntity.getHeaders());
            McpJsonRpcResponse response = objectMapper.readValue(responseEntity.getBody(), McpJsonRpcResponse.class);
            if (response.getError() != null) {
                throw new RecoverableToolException("MCP request failed for " + method + ": " + response.getError().getMessage());
            }
            return response;
        } catch (RecoverableToolException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RecoverableToolException("Failed to execute MCP request for " + method, exception);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        headers.forEach(httpHeaders::set);
        if (sessionId != null && !sessionId.isBlank()) {
            httpHeaders.set(MCP_SESSION_ID_HEADER, sessionId);
        }
        return httpHeaders;
    }

    private void cacheSessionId(HttpHeaders responseHeaders) {
        String returnedSessionId = responseHeaders.getFirst(MCP_SESSION_ID_HEADER);
        if (returnedSessionId != null && !returnedSessionId.isBlank()) {
            sessionId = returnedSessionId;
        }
    }

    private JsonNode requireResultObject(McpJsonRpcResponse response) {
        if (response == null || response.getResult() == null || response.getResult().isNull()) {
            throw new RecoverableToolException("MCP response did not contain a result");
        }
        return response.getResult();
    }

    private JsonNode parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(argumentsJson);
        } catch (Exception exception) {
            throw new RecoverableToolException("Failed to parse MCP tool arguments", exception);
        }
    }

    private String extractText(JsonNode contentNode) {
        if (!(contentNode instanceof ArrayNode contentArray)) {
            return null;
        }
        List<String> textParts = new ArrayList<>();
        for (JsonNode item : contentArray) {
            if ("text".equals(item.path("type").asText()) && item.hasNonNull("text")) {
                textParts.add(item.get("text").asText());
            }
        }
        if (textParts.isEmpty()) {
            return null;
        }
        return String.join("\n", textParts);
    }
}
