package com.agent.editor.agent.v2.core.runtime;

import com.agent.editor.agent.v2.core.agent.AgentDefinition;
import com.agent.editor.agent.v2.core.agent.AgentType;
import com.agent.editor.agent.v2.core.agent.Decision;
import com.agent.editor.agent.v2.core.agent.ToolCall;
import com.agent.editor.agent.v2.core.memory.ChatMessage;
import com.agent.editor.agent.v2.core.memory.ChatTranscriptMemory;
import com.agent.editor.agent.v2.core.state.*;
import com.agent.editor.agent.v2.trace.DefaultTraceCollector;
import com.agent.editor.agent.v2.trace.InMemoryTraceStore;
import com.agent.editor.agent.v2.trace.TraceCategory;
import com.agent.editor.agent.v2.trace.TraceStore;
import com.agent.editor.agent.v2.tool.ToolContext;
import com.agent.editor.agent.v2.tool.ToolHandler;
import com.agent.editor.agent.v2.tool.ToolInvocation;
import com.agent.editor.agent.v2.tool.ToolRegistry;
import com.agent.editor.agent.v2.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultExecutionRuntimeTest {

    @Test
    void shouldCompleteWhenAgentReturnsCompleteDecision() {
        AgentDefinition agent = new CompletingAgentDefinition();
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                new ToolRegistry(),
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-1",
                "session-1",
                AgentType.REACT,
                new DocumentSnapshot("doc-1", "title", "body"),
                "finish",
                3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("done", result.finalMessage());
        assertEquals("body", result.finalContent());
        assertEquals(ExecutionStage.COMPLETED, result.finalState().stage());
        assertEquals("body", result.finalState().currentContent());
        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.finalState().memory();
        assertEquals(2, transcriptMemory.messages().size());
        ChatMessage.UserChatMessage userMessage =
                (ChatMessage.UserChatMessage) transcriptMemory.messages().get(0);
        ChatMessage.AiChatMessage aiMessage =
                (ChatMessage.AiChatMessage) transcriptMemory.messages().get(1);
        assertEquals("finish", userMessage.text());
        assertEquals("done", aiMessage.text());
    }

    @Test
    void shouldExecuteToolCallsBeforeCompleting() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        AgentDefinition agent = new ToolUsingAgentDefinition();
        ExecutionRequest request = new ExecutionRequest(
                "task-2",
                "session-2",
                AgentType.REACT,
                new DocumentSnapshot("doc-2", "title", "body"),
                "use tool",
                3
        );

        ExecutionResult result = runtime.run(agent, request);

        assertEquals("updated", result.finalMessage());
        assertEquals("body world", result.finalContent());
    }

    @Test
    void shouldRejectToolCallsOutsideAllowedWorkerTools() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-3",
                "session-3",
                AgentType.REACT,
                new DocumentSnapshot("doc-3", "title", "body"),
                "use tool",
                3,
                List.of("searchContent")
        );

        IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                runtime.run(new ToolUsingAgentDefinition(), request)
        );

        assertEquals("No tool handler registered for appendText", error.getMessage());
    }

    @Test
    void shouldAccumulateToolResultsAcrossIterations() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-4",
                "session-4",
                AgentType.REACT,
                new DocumentSnapshot("doc-4", "title", "body"),
                "use tool twice",
                4
        );

        ExecutionResult result = runtime.run(new MultiStepToolAgentDefinition(), request);

        assertEquals("used two tools", result.finalMessage());
        assertEquals("body world world", result.finalContent());
    }

    @Test
    void shouldCaptureRuntimeTraceForStateAndToolExecution() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        TraceStore traceStore = new InMemoryTraceStore();
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(traceStore)
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-5",
                "session-5",
                AgentType.REACT,
                new DocumentSnapshot("doc-5", "title", "body"),
                "use tool",
                3
        );

        runtime.run(new ToolUsingAgentDefinition(), request);

        var traces = traceStore.getByTaskId("task-5");
        assertTrue(traces.stream().anyMatch(trace -> trace.category() == TraceCategory.STATE_SNAPSHOT));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.TOOL_INVOCATION
                        && "appendText".equals(trace.payload().get("toolName"))
                        && "{\"suffix\":\" world\"}".equals(trace.payload().get("arguments"))
        ));
        assertTrue(traces.stream().anyMatch(trace ->
                trace.category() == TraceCategory.TOOL_RESULT
                        && "appendText".equals(trace.payload().get("toolName"))
                        && "hello world".equals(trace.payload().get("message"))
                        && "body world".equals(trace.payload().get("updatedContent"))
        ));
    }

    @Test
    void shouldResumeFromProvidedExecutionState() {
        ToolRegistry registry = new ToolRegistry();
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        RecordingStateAgentDefinition agent = new RecordingStateAgentDefinition();
        ExecutionRequest request = new ExecutionRequest(
                "task-6",
                "session-6",
                AgentType.REACT,
                new DocumentSnapshot("doc-6", "title", "original body"),
                "finish",
                3
        );
        ExecutionState initialState = new ExecutionState(2, "resumed body");

        ExecutionResult result = runtime.run(agent, request, initialState);

        assertEquals(2, agent.seenIteration);
        assertEquals("resumed body", agent.seenContent);
        assertEquals("resumed", result.finalMessage());
        assertEquals("resumed body", result.finalContent());
        assertEquals(ExecutionStage.COMPLETED, result.finalState().stage());
    }

    @Test
    void shouldPreserveExistingMemoryWhenToolResultsAreAppended() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new AppendToolHandler());
        ExecutionRuntime runtime = new DefaultExecutionRuntime(
                registry,
                event -> {},
                new DefaultTraceCollector(new InMemoryTraceStore())
        );
        ExecutionRequest request = new ExecutionRequest(
                "task-7",
                "session-7",
                AgentType.REACT,
                new DocumentSnapshot("doc-7", "title", "body"),
                "use tool",
                3
        );
        ExecutionState initialState = new ExecutionState(
                0,
                "body",
                new ChatTranscriptMemory(List.of(
                        new ChatMessage.UserChatMessage("plan step 1"),
                        new ChatMessage.AiChatMessage("thinking")
                )),
                ExecutionStage.RUNNING,
                null
        );

        ExecutionResult result = runtime.run(new ToolUsingAgentDefinition(), request, initialState);

        ChatTranscriptMemory transcriptMemory = (ChatTranscriptMemory) result.finalState().memory();
        assertTrue(transcriptMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.text().contains("plan step 1")
        ));
        assertTrue(transcriptMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.AiChatMessage aiMessage
                        && aiMessage.text().contains("thinking")
        ));
        assertTrue(transcriptMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.UserChatMessage userMessage
                        && userMessage.text().contains("use tool")
        ));
        ChatMessage.AiToolCallChatMessage toolCallMessage = transcriptMemory.messages().stream()
                .filter(ChatMessage.AiToolCallChatMessage.class::isInstance)
                .map(ChatMessage.AiToolCallChatMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("need tool", toolCallMessage.text());
        assertEquals(1, toolCallMessage.toolCalls().size());
        assertEquals("appendText", toolCallMessage.toolCalls().get(0).name());
        assertEquals("{\"suffix\":\" world\"}", toolCallMessage.toolCalls().get(0).arguments());
        assertTrue(transcriptMemory.messages().stream().anyMatch(message ->
                message instanceof ChatMessage.ToolExecutionResultChatMessage toolMessage
                        && toolMessage.id() != null
                        && "appendText".equals(toolMessage.name())
                        && "{\"suffix\":\" world\"}".equals(toolMessage.argument())
                        && toolMessage.text().contains("hello world")
        ));
    }

    private static final class CompletingAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            return new Decision.Complete("done", "complete immediately");
        }
    }

    private static final class ToolUsingAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            if (context.state().toolResults().isEmpty()) {
                return new Decision.ToolCalls(List.of(new ToolCall("appendText", "{\"suffix\":\" world\"}")), "need tool");
            }
            return new Decision.Complete("updated", "tool finished");
        }
    }

    private static final class AppendToolHandler implements ToolHandler {

        @Override
        public String name() {
            return "appendText";
        }

        @Override
        public ToolResult execute(ToolInvocation invocation, ToolContext context) {
            return new ToolResult("hello world", context.currentContent() + " world");
        }

        @Override
        public dev.langchain4j.agent.tool.ToolSpecification specification() {
            return dev.langchain4j.agent.tool.ToolSpecification.builder()
                    .name("appendText")
                    .description("Append text to the current document content")
                    .parameters(dev.langchain4j.model.chat.request.json.JsonObjectSchema.builder()
                            .addStringProperty("suffix")
                            .required("suffix")
                            .build())
                    .build();
        }
    }

    private static final class MultiStepToolAgentDefinition implements AgentDefinition {

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            if (context.state().toolResults().size() < 2) {
                return new Decision.ToolCalls(List.of(new ToolCall("appendText", "{\"suffix\":\" world\"}")), "need another tool run");
            }
            return new Decision.Complete("used two tools", "tool history available");
        }
    }

    private static final class RecordingStateAgentDefinition implements AgentDefinition {

        private int seenIteration;
        private String seenContent;

        @Override
        public AgentType type() {
            return AgentType.REACT;
        }

        @Override
        public Decision decide(ExecutionContext context) {
            seenIteration = context.state().iteration();
            seenContent = context.state().currentContent();
            return new Decision.Complete("resumed", "resumed execution");
        }
    }
}
