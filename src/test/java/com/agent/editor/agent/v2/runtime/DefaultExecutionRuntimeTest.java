package com.agent.editor.agent.v2.runtime;

import com.agent.editor.agent.v2.definition.AgentDefinition;
import com.agent.editor.agent.v2.definition.AgentType;
import com.agent.editor.agent.v2.definition.Decision;
import com.agent.editor.agent.v2.definition.ToolCall;
import com.agent.editor.agent.v2.state.DocumentSnapshot;
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
}
