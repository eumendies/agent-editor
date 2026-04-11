package com.agent.editor.config;

import com.agent.editor.agent.mcp.config.McpProperties;
import com.agent.editor.agent.mcp.config.McpServerProperties;
import com.agent.editor.agent.mcp.config.McpToolProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class McpPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "agent.mcp.servers.web-search.type=streamableHttp",
                    "agent.mcp.servers.web-search.description=Aliyun Bailian WebSearch MCP server",
                    "agent.mcp.servers.web-search.active=true",
                    "agent.mcp.servers.web-search.name=AliyunBailianMCP_WebSearch",
                    "agent.mcp.servers.web-search.base-url=https://example.test/mcp",
                    "agent.mcp.servers.web-search.headers.Authorization=Bearer test-key",
                    "agent.mcp.servers.web-search.tools[0].tool-name=webSearch",
                    "agent.mcp.servers.web-search.tools[0].remote-tool-name=webSearch",
                    "agent.mcp.servers.web-search.tools[0].description=Search the public web"
            );

    @Test
    void shouldBindActiveStreamableHttpServerAndToolMapping() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(McpProperties.class);

            McpProperties properties = context.getBean(McpProperties.class);
            assertThat(properties.getServers()).containsKey("web-search");

            McpServerProperties server = properties.getServers().get("web-search");
            assertThat(server.getType()).isEqualTo("streamableHttp");
            assertThat(server.isActive()).isTrue();
            assertThat(server.getBaseUrl()).isEqualTo("https://example.test/mcp");
            assertThat(server.getHeaders()).containsEntry("Authorization", "Bearer test-key");

            assertThat(server.getTools()).hasSize(1);
            McpToolProperties tool = server.getTools().get(0);
            assertThat(tool.getToolName()).isEqualTo("webSearch");
            assertThat(tool.getRemoteToolName()).isEqualTo("webSearch");
            assertThat(tool.getDescription()).isEqualTo("Search the public web");
        });
    }

    @Configuration
    @EnableConfigurationProperties(McpProperties.class)
    static class TestConfig {
    }
}
