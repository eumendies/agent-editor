package com.agent.editor.config;

import com.agent.editor.agent.mcp.client.McpClient;
import com.agent.editor.agent.mcp.client.McpToolDescriptor;
import com.agent.editor.agent.mcp.config.McpProperties;
import com.agent.editor.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class McpConfigTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ToolConfig.class, TestMcpConfig.class);

    @Test
    void shouldRegisterActiveMappedMcpToolIntoToolRegistry() {
        TestMcpConfig.mcpClient = new StubMcpClient(List.of(
                new McpToolDescriptor(
                        "webSearch",
                        "Search the public web",
                        inputSchema()
                )
        ));

        contextRunner.withPropertyValues(
                "agent.mcp.servers.web-search.type=streamableHttp",
                "agent.mcp.servers.web-search.active=true",
                "agent.mcp.servers.web-search.base-url=https://example.test/mcp",
                "agent.mcp.servers.web-search.tools[0].tool-name=webSearch",
                "agent.mcp.servers.web-search.tools[0].remote-tool-name=webSearch"
        ).run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get("webSearch")).isNotNull();
        });
    }

    @Test
    void shouldSkipInactiveServerMappings() {
        TestMcpConfig.mcpClient = new StubMcpClient(List.of(
                new McpToolDescriptor("webSearch", "Search the public web", OBJECT_MAPPER.createObjectNode())
        ));

        contextRunner.withPropertyValues(
                "agent.mcp.servers.web-search.type=streamableHttp",
                "agent.mcp.servers.web-search.active=false",
                "agent.mcp.servers.web-search.base-url=https://example.test/mcp",
                "agent.mcp.servers.web-search.tools[0].tool-name=webSearch",
                "agent.mcp.servers.web-search.tools[0].remote-tool-name=webSearch"
        ).run(context -> {
            ToolRegistry toolRegistry = context.getBean(ToolRegistry.class);

            assertThat(toolRegistry.get("webSearch")).isNull();
        });
    }

    @Configuration
    @EnableConfigurationProperties(McpProperties.class)
    static class TestMcpConfig extends McpConfig {

        private static McpClient mcpClient;

        TestMcpConfig(McpProperties properties) {
            super(properties);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Override
        protected McpClient createClient(String serverKey,
                                         com.agent.editor.agent.mcp.config.McpServerProperties serverProperties,
                                         ObjectMapper objectMapper) {
            return mcpClient;
        }
    }

    private record StubMcpClient(List<McpToolDescriptor> descriptors) implements McpClient {

        @Override
        public List<McpToolDescriptor> listTools() {
            return descriptors;
        }

        @Override
        public com.agent.editor.agent.mcp.client.McpToolCallResult callTool(String toolName, String argumentsJson) {
            return new com.agent.editor.agent.mcp.client.McpToolCallResult(false, null, "unused");
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode inputSchema() {
        com.fasterxml.jackson.databind.node.ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        com.fasterxml.jackson.databind.node.ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string");
        schema.putArray("required").add("query");
        return schema;
    }
}
