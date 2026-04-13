package com.agent.editor.config;

import com.agent.editor.agent.mcp.client.McpClient;
import com.agent.editor.agent.mcp.client.McpToolDescriptor;
import com.agent.editor.agent.mcp.client.SdkMcpClientAdapter;
import com.agent.editor.agent.mcp.config.McpProperties;
import com.agent.editor.agent.mcp.config.McpServerProperties;
import com.agent.editor.agent.mcp.config.McpToolProperties;
import com.agent.editor.agent.mcp.tool.McpBackedToolHandler;
import com.agent.editor.agent.mcp.tool.McpToolResultFormatter;
import com.agent.editor.agent.tool.ToolHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring wiring for MCP-backed tools.
 * <p>
 * This configuration reads declared MCP servers, creates SDK-backed clients,
 * and registers only the explicitly mapped remote tools into the local tool
 * registry pipeline.
 */
@Configuration
public class McpConfig {

    private final McpProperties properties;

    public McpConfig(McpProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates local tool handlers for every active MCP server/tool mapping.
     *
     * @param objectMapper mapper used for result formatting and schema normalization
     * @return immutable list of MCP-backed tool handlers
     */
    @Bean
    public List<ToolHandler> mcpToolHandlers(ObjectMapper objectMapper) {
        List<ToolHandler> handlers = new ArrayList<>();
        McpToolResultFormatter resultFormatter = new McpToolResultFormatter(objectMapper);
        for (Map.Entry<String, McpServerProperties> entry : properties.getServers().entrySet()) {
            if (!entry.getValue().isActive()) {
                continue;
            }
            handlers.addAll(buildHandlers(entry.getKey(), entry.getValue(), objectMapper, resultFormatter));
        }
        return List.copyOf(handlers);
    }

    /**
     * Wraps the SDK client inside the local {@link McpClient} abstraction.
     *
     * @param serverKey logical server key from configuration
     * @param serverProperties MCP server settings
     * @param objectMapper mapper used for schema/result normalization
     * @return MCP client adapter consumed by local tool handlers
     */
    protected McpClient createClient(String serverKey,
                                     McpServerProperties serverProperties,
                                     ObjectMapper objectMapper) {
        validateServer(serverKey, serverProperties);
        return new SdkMcpClientAdapter(
                createSdkClient(serverKey, serverProperties),
                objectMapper
        );
    }

    /**
     * Creates the underlying LangChain4j MCP SDK client for a configured
     * streamable HTTP server.
     *
     * @param serverKey logical server key from configuration
     * @param serverProperties MCP server settings
     * @return SDK MCP client with transport/session handling managed by the SDK
     */
    protected dev.langchain4j.mcp.client.McpClient createSdkClient(String serverKey,
                                                                   McpServerProperties serverProperties) {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url(serverProperties.getBaseUrl())
                .customHeaders(serverProperties.getHeaders())
                .build();
        return DefaultMcpClient.builder()
                .key(serverKey)
                .clientName("ai-editor")
                .clientVersion("1.0.0")
                .transport(transport)
                .build();
    }

    private List<ToolHandler> buildHandlers(String serverKey,
                                            McpServerProperties serverProperties,
                                            ObjectMapper objectMapper,
                                            McpToolResultFormatter resultFormatter) {
        // 启动时先拉远端工具清单，只注册配置中白名单允许暴露给模型的本地工具映射。
        validateServer(serverKey, serverProperties);
        McpClient client = createClient(serverKey, serverProperties, objectMapper);
        Map<String, McpToolDescriptor> remoteTools = new LinkedHashMap<>();
        for (McpToolDescriptor descriptor : client.listTools()) {
            remoteTools.put(descriptor.getName(), descriptor);
        }

        List<ToolHandler> handlers = new ArrayList<>();
        for (McpToolProperties toolProperties : serverProperties.getTools()) {
            McpToolDescriptor descriptor = remoteTools.get(toolProperties.getRemoteToolName());
            if (descriptor == null) {
                throw new IllegalStateException("Remote MCP tool is not available for server " + serverKey + ": " + toolProperties.getRemoteToolName());
            }
            handlers.add(new McpBackedToolHandler(toolProperties, descriptor, client, resultFormatter));
        }
        return handlers;
    }

    private void validateServer(String serverKey, McpServerProperties serverProperties) {
        if (!"streamableHttp".equals(serverProperties.getType())) {
            throw new IllegalStateException("Unsupported MCP server type for " + serverKey + ": " + serverProperties.getType());
        }
        if (serverProperties.getBaseUrl() == null || serverProperties.getBaseUrl().isBlank()) {
            throw new IllegalStateException("MCP baseUrl is required for " + serverKey);
        }
        // active server 必须显式声明允许暴露的本地工具映射，避免把远端全部工具直接透传给模型。
        if (serverProperties.getTools() == null || serverProperties.getTools().isEmpty()) {
            throw new IllegalStateException("At least one MCP tool mapping is required for " + serverKey);
        }
    }
}
