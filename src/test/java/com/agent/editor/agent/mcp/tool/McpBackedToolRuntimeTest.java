package com.agent.editor.agent.mcp.tool;

import com.agent.editor.agent.core.agent.AgentType;
import com.agent.editor.agent.core.agent.ToolCall;
import com.agent.editor.agent.core.agent.ToolLoopAgent;
import com.agent.editor.agent.core.agent.ToolLoopDecision;
import com.agent.editor.agent.core.context.AgentRunContext;
import com.agent.editor.agent.core.memory.ChatMessage;
import com.agent.editor.agent.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.core.runtime.ExecutionRequest;
import com.agent.editor.agent.core.runtime.ExecutionResult;
import com.agent.editor.agent.core.runtime.ToolLoopExecutionRuntime;
import com.agent.editor.agent.core.state.DocumentSnapshot;
import com.agent.editor.agent.mcp.client.McpClient;
import com.agent.editor.agent.mcp.client.McpToolCallResult;
import com.agent.editor.agent.mcp.client.McpToolDescriptor;
import com.agent.editor.agent.mcp.config.McpToolProperties;
import com.agent.editor.agent.tool.RecoverableToolException;
import com.agent.editor.agent.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpBackedToolRuntimeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldAppendWebSearchResultToToolMemoryWithoutUpdatedContent() {
        McpClient client = mock(McpClient.class);
        when(client.callTool("webSearch", "{\"query\":\"latest ai news\"}"))
                .thenReturn(new McpToolCallResult(
                        false,
                        OBJECT_MAPPER.createObjectNode().putArray("items").addObject().put("title", "news"),
                        "result text"
                ));

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(webSearchHandler(client));
        ToolLoopExecutionRuntime runtime = new ToolLoopExecutionRuntime(toolRegistry, event -> {});

        ExecutionResult result = runtime.run(
                new SingleWebSearchAgent("{\"query\":\"latest ai news\"}"),
                new ExecutionRequest(
                        "task-web-1",
                        "session-web-1",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-1", "title", "body"),
                        "search web",
                        3
                )
        );

        assertThat(result.getFinalContent()).isEqualTo("body");
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertThat(transcriptMemory.getMessages()).anySatisfy(message -> {
            assertThat(message).isInstanceOf(ChatMessage.ToolExecutionResultChatMessage.class);
            ChatMessage.ToolExecutionResultChatMessage toolMessage = (ChatMessage.ToolExecutionResultChatMessage) message;
            assertThat(toolMessage.getName()).isEqualTo("webSearch");
            assertThat(toolMessage.getText()).contains("\"structuredContent\"");
            assertThat(toolMessage.getText()).contains("\"title\":\"news\"");
        });
    }

    @Test
    void shouldContinueLoopAfterRecoverableMcpFailure() {
        McpClient client = mock(McpClient.class);
        when(client.callTool("webSearch", "{\"query\":\"latest ai news\"}"))
                .thenThrow(new RecoverableToolException("upstream timeout"));

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(webSearchHandler(client));
        ToolLoopExecutionRuntime runtime = new ToolLoopExecutionRuntime(toolRegistry, event -> {});

        ExecutionResult result = runtime.run(
                new SingleWebSearchAgent("{\"query\":\"latest ai news\"}"),
                new ExecutionRequest(
                        "task-web-2",
                        "session-web-2",
                        AgentType.REACT,
                        new DocumentSnapshot("doc-2", "title", "body"),
                        "search web",
                        3
                )
        );

        assertThat(result.getFinalMessage()).isEqualTo("complete after tool");
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.getFinalState().getMemory();
        assertThat(transcriptMemory.getMessages()).anySatisfy(message -> {
            assertThat(message).isInstanceOf(ChatMessage.ToolExecutionResultChatMessage.class);
            ChatMessage.ToolExecutionResultChatMessage toolMessage = (ChatMessage.ToolExecutionResultChatMessage) message;
            assertThat(toolMessage.getName()).isEqualTo("webSearch");
            assertThat(toolMessage.getText()).contains("\"status\":\"error\"");
            assertThat(toolMessage.getText()).contains("upstream timeout");
        });
    }

    private McpBackedToolHandler webSearchHandler(McpClient client) {
        McpToolProperties properties = new McpToolProperties();
        properties.setToolName("webSearch");
        properties.setRemoteToolName("webSearch");
        properties.setDescription("Search real-time public web information");

        ObjectNode schema = OBJECT_MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties").putObject("query").put("type", "string");
        schema.putArray("required").add("query");

        return new McpBackedToolHandler(
                properties,
                new McpToolDescriptor("webSearch", "Search the public web", schema),
                client,
                new McpToolResultFormatter(OBJECT_MAPPER)
        );
    }

    private static final class SingleWebSearchAgent implements ToolLoopAgent {

        private final String arguments;

        private SingleWebSearchAgent(String arguments) {
            this.arguments = arguments;
        }

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public ToolLoopDecision decide(AgentRunContext context) {
            if (toolResultCount(context) == 0) {
                return new ToolLoopDecision.ToolCalls(
                        List.of(new ToolCall("webSearch", arguments)),
                        "need web search"
                );
            }
            return new ToolLoopDecision.Complete("complete after tool", "done");
        }

        private long toolResultCount(AgentRunContext context) {
            if (!(context.state().getMemory() instanceof ChatTranscriptMemory memory)) {
                return 0;
            }
            return memory.getMessages().stream()
                    .filter(ChatMessage.ToolExecutionResultChatMessage.class::isInstance)
                    .count();
        }
    }
}
